package rs.ruffle

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.ruffle.ui.theme.RuffleTheme

class MainActivity : ComponentActivity() {

    private val servers = mapOf(
        "Twilly" to "socket5.aq.com:5588",
        "Artix" to "socket.aq.com:5588",
        "Gravelyn" to "socket4.aq.com:5589",
        "Sir Ver" to "socket2.aq.com:5588",
        "Galanoth" to "socket6.aq.com:5589",
        "Yorumi" to "socket3.aq.com:5588",
        "Espada" to "socket2.aq.com:5591",
        "Twig" to "socket4.aq.com:5588",
        "Sepulchure" to "socket2.aq.com:5590",
        "Safiria" to "socket6.aq.com:5588",
        "Swordhaven" to "euro.aqw.artix.com:5588",
        "Alteon" to "socket4.aq.com:5590",
        "Yokai" to "asia.game.artix.com:5588",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RuffleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                        ServerSelector()
                    }
                }
            }
        }
    }

    @Composable
    fun ServerSelector() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AQW Mobile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(servers.entries.toList()) { (serverName, serverInfo) ->
                    Button(
                        onClick = { launchGame(serverInfo) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(serverName)
                    }
                }
            }
        }
    }

    private fun launchGame(serverInfo: String) {
        val host = serverInfo.substringBefore(":")
        val port = serverInfo.substringAfter(":").toInt()

        val intent = Intent(this, PlayerActivity::class.java).apply {
            data = Uri.parse("https://game.aq.com/game/gamefiles/Loader_Spider.swf")
            putExtra("aqw_host", host)
            putExtra("aqw_port", port)
        }
        startActivity(intent)
    }
}