package rs.ruffle

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.androidgamesdk.GameActivity
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import android.view.inputmethod.InputMethodManager
import android.text.Editable
import android.text.TextWatcher

class PlayerActivity : GameActivity() {

    // --- AQW ---
    private var aqwBridge: AqwBridge? = null
    private external fun nativeOnTextInput(text: String)
    private external fun nativeOnBackspace()
    // --- AQW END ---

    @Suppress("unused")
    private val swfBytes: ByteArray?
        get() {
            val uri = intent.data
            if (uri?.scheme == "content") {
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) return null
                        val bytes = ByteArray(inputStream.available())
                        DataInputStream(inputStream).readFully(bytes)
                        return bytes
                    }
                } catch (ignored: IOException) {}
            }
            return null
        }

    @Suppress("unused")
    private val swfUri: String?
        get() = intent.dataString

    @Suppress("unused")
    private val traceOutput: String?
        get() = intent.getStringExtra("traceOutput")

    @Suppress("unused")
    private fun navigateToUrl(url: String?) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private var loc = IntArray(2)

    @Suppress("unused")
    private val eventLoopHandle: Long = 0

    @Suppress("unused")
    private val locInWindow: IntArray
        get() {
            mSurfaceView.getLocationInWindow(loc)
            return loc
        }

    @Suppress("unused")
    private val surfaceWidth: Int
        get() = mSurfaceView.width

    @Suppress("unused")
    private val surfaceHeight: Int
        get() = mSurfaceView.height

    private external fun keydown(keyTag: String)
    private external fun keyup(keyTag: String)
    private external fun requestContextMenu()
    private external fun runContextMenuCallback(index: Int)
    private external fun clearContextMenu()

    @Suppress("unused")
    private fun showContextMenu(items: Array<String>) {
        runOnUiThread {
            val popup = PopupMenu(this, findViewById(R.id.button_cm))
            val menu = popup.menu
            if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
                menu.setGroupDividerEnabled(true)
            }
            var group = 1
            for (i in items.indices) {
                val elements = items[i].split(" ".toRegex(), limit = 4).toTypedArray()
                val enabled = elements[0].toBoolean()
                val separatorBefore = elements[1].toBoolean()
                val checked = elements[2].toBoolean()
                val caption = elements[3]
                if (separatorBefore) group += 1
                val item = menu.add(group, i, Menu.NONE, caption)
                item.setEnabled(enabled)
                if (checked) {
                    item.setCheckable(true)
                    item.setChecked(true)
                }
            }
            val exitItemId: Int = items.size
            menu.add(group, exitItemId, Menu.NONE, "Exit")
            popup.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == exitItemId) {
                    finish()
                } else {
                    runContextMenuCallback(item.itemId)
                }
                true
            }
            popup.setOnDismissListener { clearContextMenu() }
            popup.show()
        }
    }

    @Suppress("unused")
    private fun getAndroidDataStorageDir(): String {
        val storageDirPath = "${filesDir.absolutePath}/ruffle/shared_objects"
        val storageDir = File(storageDirPath)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDirPath
    }

    // --- AQW MODIFIED UI ---
    override fun onCreateSurfaceView() {
        val inflater = layoutInflater
        @SuppressLint("InflateParams")
        val layout = inflater.inflate(R.layout.keyboard, null) as ConstraintLayout
        contentViewId = View.generateViewId()
        layout.id = contentViewId
        setContentView(layout)

        mSurfaceView = InputEnabledSurfaceView(this)
        mSurfaceView.contentDescription = "Ruffle Player"

        val placeholder = findViewById<View>(R.id.placeholder)
        val pars = placeholder.layoutParams as ConstraintLayout.LayoutParams
        val parent = placeholder.parent as ViewGroup
        val index = parent.indexOfChild(placeholder)
        parent.removeView(placeholder)
        parent.addView(mSurfaceView, index)
        mSurfaceView.setLayoutParams(pars)

        // Setup the Hidden EditText to handle keyboard input
        val hiddenInput = layout.findViewById<EditText>(R.id.hidden_input)
        hiddenInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                if (text.isNotEmpty()) {
                    nativeOnTextInput(text) // Send to Rust
                    s?.clear()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        hiddenInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                nativeOnBackspace()
                true
            } else false
        }

        layout.findViewById<View>(R.id.button_kb).setOnClickListener {
            hiddenInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenInput, InputMethodManager.SHOW_FORCED)
        }

        layout.findViewById<View>(R.id.button_cm).setOnClickListener { requestContextMenu() }
        mSurfaceView.holder.addCallback(this)
        ViewCompat.setOnApplyWindowInsetsListener(mSurfaceView, this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val keyboard = findViewById<View>(R.id.keyboard)
        keyboard?.visibility = View.GONE
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val decorView = window.decorView
        val controller = WindowInsetsControllerCompat(window, decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.displayCutout())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        nativeInit { message ->
            Log.e("ruffle", "Handling panic: $message")
            startActivity(Intent(this, PanicActivity::class.java).apply { putExtra("message", message) })
        }

        // AQW custom network bridge
        val host = intent.getStringExtra("aqw_host") ?: "socket.aq.com"
        val port = intent.getIntExtra("aqw_port", 5588)
        try {
            aqwBridge = AqwBridge(8181, host, port)
            aqwBridge?.start()
        } catch (e: Exception) {
            Log.e("AQW", "Failed to start bridge: ${e.message}")
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // AQW Stop bridge when player closes
        aqwBridge?.stop()
    }

    @Suppress("unused")
    val isGooglePlayGames: Boolean
        get() = packageManager.hasSystemFeature("com.google.android.play.feature.HPE_EXPERIENCE")

    private fun requestNoStatusBarFeature() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowInsetsControllerCompat(window, mSurfaceView).hide(WindowInsetsCompat.Type.statusBars())
    }

    companion object {
        init {
            System.loadLibrary("ruffle_android")
        }

        @JvmStatic
        private external fun nativeInit(crashCallback: CrashCallback)

        private fun <T> gatherAllDescendantsOfType(v: View, t: Class<*>): List<T> {
            val result: MutableList<T> = ArrayList()
            @Suppress("UNCHECKED_CAST")
            if (t.isInstance(v)) result.add(v as T)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    result.addAll(gatherAllDescendantsOfType(v.getChildAt(i), t))
                }
            }
            return result
        }
    }

    fun interface CrashCallback {
        fun onCrash(message: String)
    }
}