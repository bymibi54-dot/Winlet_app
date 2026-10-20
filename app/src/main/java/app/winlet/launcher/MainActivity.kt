@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.winlet.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Desktop() }
    }
}

@Composable
fun TaskbarApp(a: AppItem) {
    val ctx = LocalContext.current
    val isOpen = Session.opened.any { it.pkg == a.pkg }
    Column(
        Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { launchApp(ctx, a) },
                onLongClick = { openPanel(ctx, AppMenuActivity::class.java, 280, 450, false, a.pkg) }
            ).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(a.icon, a.label, Modifier.size(30.dp))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(width = 14.dp, height = 3.dp).clip(CircleShape)
            .background(if (isOpen) AccentColor else Color.Transparent))
    }
}

@Composable
fun Desktop() {
    val ctx = LocalContext.current
    remember { Session.init(ctx) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val n = Date()
            time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(n)
            date = SimpleDateFormat("d/M/yyyy", Locale.getDefault()).format(n)
            delay(15_000)
        }
    }
    val items = Session.pinned.mapNotNull { p -> Session.find(p) } +
        Session.opened.filter { it.pkg !in Session.pinned }

    MaterialTheme(colorScheme = darkColorScheme(primary = AccentColor)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .height(60.dp).clip(RoundedCornerShape(20.dp)).background(GlassColor)
                    .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { openPanel(ctx, StartActivity::class.java, 440, 520, false) }) {
                    Icon(Icons.Default.GridView, "Start", tint = AccentColor, modifier = Modifier.size(28.dp))
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically) {
                    items.forEach { TaskbarApp(it) }
                }
                if (Session.opened.isNotEmpty()) {
                    IconButton(onClick = { Session.closeAll(ctx) }) {
                        Icon(Icons.Default.ClearAll, "Close all", tint = Color.White.copy(0.8f))
                    }
                }
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .clickable { openPanel(ctx, QuickActivity::class.java, 320, 420, true) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(time, fontSize = 12.sp, color = Color.White)
                    Text(date, fontSize = 10.sp, color = Color.White.copy(0.65f))
                }
            }
        }
    }
}
