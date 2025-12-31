These are the modifications to ruffle-android required to get AQW running.

### 1. Networking & Game Logic Implementation

#### 1.1. Local TCP Proxy (`AqwBridge.kt`)
*   **Problem:** The Ruffle engine couldn't initiate outbound TCP connections to arbitrary ports (e.g. port 5588 for the Artix servers). However, the Android application framework (Kotlin/Java) has full permission to do so.
*   **Solution:** A new class, `AqwBridge.kt`, was implemented. This class runs a `ServerSocket` on a background thread, listening for connections on `127.0.0.1:8181`. When the Ruffle engine attempts a connection, this bridge accepts it, establishes a new, real TCP `Socket` connection to the target server, and then bi-directionally pipes raw byte streams between the two sockets.
*   **Policy File Handshake:** Flash's requires a socket policy file handshake. Before sending game data, the client sends the string `<policy-file-request/>\0`. The bridge will detect this initial packet and upon receipt, immediately serve a permissive cross-domain policy XML (`<allow-access-from domain="*" to-ports="*"/>`) and closes the connection. The game client then makes a second connection for the actual game data, which the bridge proceeds to tunnel. (Without this handshake, the game would hang indefinitely.)

#### **1.2. Native Engine Redirection (`navigator.rs`)**
*   **Problem:** I needed to force the Ruffle engine to connect to the local `AqwBridge` instead of the real Artix servers.
*   **Solution:** I performed a direct modification ("monkey-patch") of the Ruffle library source code within the local Cargo cache. Specifically, in `frontend-utils/src/backends/navigator.rs`, the `connect_socket` function was altered. At the entry point of this function, the `host` and `port` parameters are now hardcoded to `"127.0.0.1"` and `8181`, respectively. This guarantees that any TCP socket attempt initiated by the Flash content is transparently redirected to our Kotlin proxy.

#### **1.3. Game Asset Loading (`lib.rs`)**
*   **Problem:** After fixing the connection, the game would load the main SWF but then crash or fail to display assets, throwing a `JSON Error #1132`. The AQW loader expects its base asset path to be passed as a FlashVar (`base=...`), not as a URL query parameter. My initial attempt to append it to the URL caused the loader to misinterpret the URL string when making subsequent data requests.
*   **Solution:** The logic in `lib.rs`'s `InitWindow` block was refactored. A `Vec` of key-value string pairs (FlashVars) containing `"base"` and `"allowNetworking"` is now passed during the movie fetching process. This provides the SWF with the correct context to resolve its asset paths (e.g. maps, items).

### 2. Input System

I replaced the default keyboard with the native Android keyboard.

*   **Problem:** The default keyboard sent discrete `KeyDown` events, which is incompatible with modern Android soft keyboards (like Gboard) that primarily use `commitText` to send composed strings, not individual key presses. This meant no text would appear.
*   **Solution:** I used a hidden EditText to capture and process text.
    1.  **Layout (`keyboard.xml`):** A 1x1 pixel, transparent `EditText` view was added to the layout.
    2.  **Logic (`PlayerActivity.kt`):** The "KB" button in the UI now gives focus to this hidden `EditText`, which triggers the appearance of the user's keyboard.
    3.  **Data Bridge:** A `TextWatcher` is attached to the hidden `EditText`. When text is committed the `afterTextChanged` callback fires. This callback immediately calls a new JNI function (`nativeOnTextInput`), passes the typed character(s) to the Rust engine, and then clears the `EditText`.
*   **JNI and Rust Implementation (`lib.rs`, `custom_event.rs`):** To receive the text, the Rust backend was extended. A new `RuffleEvent::TextInput` variant was created. A corresponding JNI function, `Java_rs_ruffle_PlayerActivity_nativeOnTextInput`, was exposed. This function receives the string from Kotlin, iterates through its characters, and dispatches them into the Ruffle event loop. The event loop then creates a `PlayerEvent::TextInput`, which the Ruffle core processes and forwards to the focused Flash text field. A similar JNI function was attempted for backspace, but didn't work.

### 3. Launcher UI

*   **Launcher UI (`MainActivity.kt`):** The default file-picker was replaced with a custom Jetpack Compose UI. A `LazyVerticalGrid` was implemented to mimic the in-game server selection screen.
*   **Full-Screen (`AndroidManifest.xml`, `keyboard.xml`):** The `PlayerActivity` was set to use a `NoActionBar` theme in the manifest, removing the top title bar. The layout constraints in `keyboard.xml` were adjusted to make the game's `SurfaceView` expand to fill the entire screen, with the UI toolbar floating transparently at the bottom.
*   **App Branding:** The default Ruffle icons in all `mipmap-*` directories were replaced using Android Studio's "Image Asset Studio". The application name was changed in `strings.xml`.

### **4. Build System / Release Management**

The project's build pipeline was broken due to upstream dependency updates (I assume) and required manual intervention.

*   **Problem:** Something broke the `cargo-ndk-android` Gradle plugin, causing an `exec()` method error and preventing any compilation of the Rust core.
*   **Solution:** The solution was to take over the plugin's responsibilities.
    1.  **Manual Build:** I used the `cargo ndk` command directly from the terminal to compile the Rust code into the necessary `.so` native libraries. This required specifying `--platform 26` to resolve a linker error where the `aaudio` library could not be found.
    2.  **Plugin Disablement:** The `cargo-ndk-android` plugin was completely commented out in `app/build.gradle.kts`. This prevents the broken task from running.
*   **Release Signing:** To produce a distributable app, a private release keystore (`.jks` file) was generated using `keytool`. I stored the keystore passwords and alias in  `gradle.properties` (and added it to `.gitignore`). The `signingConfigs` block in `app/build.gradle.kts` was modified to read these properties, enabling the build of a signed release APK via the `./gradlew assembleRelease` command.