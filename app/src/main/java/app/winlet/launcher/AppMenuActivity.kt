package app.winlet.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AppMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        val app = intent.getStringExtra("pkg")?.let { Session.find(it) }
        if (app == null) { finish(); return }
        setContent { AppMenu(app) { finish() } }
    }
    override fun onPause() { super.onPause(); if (!isFinishing) finish() }
}

@Composable
fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
fun AppMenu(app: AppItem, done: () -> Unit) {
    val ctx = LocalContext.current
    val pinned = app.pkg in Session.pinned
    val isOpen = Session.opened.any { it.pkg == app.pkg }
    val uri = Uri.parse("package:${app.pkg}")
    fun act(f: () -> Unit) { f(); done() }
    MaterialTheme(colorScheme = darkColorScheme(primary = AccentColor)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(GlassColor)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(10.dp)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, app.label, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            MenuRow(Icons.Default.PlayArrow, "Open (floating)") { act { launchApp(ctx, app, Layout.FLOAT) } }
            MenuRow(Icons.Default.ChevronLeft, "Snap to left half") { act { launchApp(ctx, app, Layout.LEFT) } }
            MenuRow(Icons.Default.ChevronRight, "Snap to right half") { act { launchApp(ctx, app, Layout.RIGHT) } }
            MenuRow(Icons.Default.Fullscreen, "Maximize") { act { launchApp(ctx, app, Layout.FULL) } }
            MenuRow(Icons.Default.PushPin, if (pinned) "Unpin from taskbar" else "Pin to taskbar") {
                act { Session.togglePin(ctx, app.pkg) }
            }
            MenuRow(Icons.Default.Info, "App info") {
                act { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            MenuRow(Icons.Default.Delete, "Uninstall") {
                act { ctx.startActivity(Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            if (isOpen) MenuRow(Icons.Default.Close, "Close") { act { Session.close(ctx, app.pkg) } }
        }
    }
}
