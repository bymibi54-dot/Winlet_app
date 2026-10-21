@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.winlet.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Desktop() }
    }

    override fun onResume() {
        super.onResume()
        Session.resumeTick.intValue++
    }
}

private val BarColor = Color(0xEB1C1C1E)

/** Opens the notification shade (best effort, needs the normal EXPAND_STATUS_BAR permission). */
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (_: Throwable) { }
}

@Composable
fun WinLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val s = (size.width - gap) / 2f
        val brush = Brush.linearGradient(
            colors = listOf(Color(0xFF5DD3FF), Color(0xFF1B6FE0)),
            start = Offset.Zero, end = Offset(size.width, size.height)
        )
        val r = CornerRadius(1.5.dp.toPx())
        for (x in 0..1) for (y in 0..1)
            drawRoundRect(brush, Offset(x * (s + gap), y * (s + gap)), Size(s, s), r)
    }
}

@Composable
fun TaskbarApp(a: AppItem) {
    val ctx = LocalContext.current
    val isOpen = Session.opened.any { it.pkg == a.pkg }
    Column(
        Modifier.padding(horizontal = 1.dp).clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { launchApp(ctx, a) },
                onLongClick = { openPanel(ctx, AppMenuActivity::class.java, 280, 450, false, a.pkg) }
            ).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(a.icon, a.label, Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Box(Modifier.size(width = if (isOpen) 14.dp else 4.dp, height = 3.dp).clip(CircleShape)
            .background(if (isOpen) AccentColor else Color.Transparent))
    }
}

@Composable
fun Taskbar() {
    val ctx = LocalContext.current
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val is24 = remember { android.text.format.DateFormat.is24HourFormat(ctx) }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var level by remember { mutableIntStateOf(100) }
    var charging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val n = Date()
            time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(n)
            date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(n)
            delay(10_000)
        }
    }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 /
                    i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val sticky = ContextCompat.registerReceiver(
            ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        sticky?.let { r.onReceive(ctx, it) }
        onDispose { ctx.unregisterReceiver(r) }
    }

    val items = Session.pinned.mapNotNull { p -> Session.find(p) } +
        Session.opened.filter { it.pkg !in Session.pinned }
    val openQuick = { openPanel(ctx, QuickActivity::class.java, 320, 420, true) }
    val openStart = { openPanel(ctx, StartActivity::class.java, 440, 520, false) }

    Column(Modifier.fillMaxWidth().background(BarColor)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(0.08f)))
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
            val wide = maxWidth >= 600.dp
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Centered group: logo, search, apps
                Row(
                    Modifier.weight(1f).padding(start = if (wide) 190.dp else 0.dp),
                    horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)).clickable { openStart() },
                        contentAlignment = Alignment.Center) { WinLogo(Modifier.size(20.dp)) }
                    if (wide) {
                        Row(
                            Modifier.padding(horizontal = 4.dp).width(150.dp).height(34.dp)
                                .clip(RoundedCornerShape(50)).background(Color.White.copy(0.08f))
                                .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(50))
                                .clickable { openStart() }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Search", fontSize = 13.sp, color = Color.White.copy(0.7f))
                        }
                    } else {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)).clickable { openStart() },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically) {
                        items.forEach { TaskbarApp(it) }
                    }
                }
                // System tray
                if (Session.opened.isNotEmpty()) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable { Session.closeAll(ctx) },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ClearAll, "Close all", tint = Color.White.copy(0.85f),
                            modifier = Modifier.size(18.dp))
                    }
                }
                if (wide) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { openQuick() },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { openQuick() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (wide) {
                        Icon(Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(
                        when { charging -> Icons.Default.BatteryChargingFull
                               level <= 15 -> Icons.Default.BatteryAlert
                               else -> Icons.Default.BatteryFull },
                        null, tint = if (level <= 15 && !charging) Color(0xFFFF6B6B) else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { openQuick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(time, fontSize = 12.sp, color = Color.White)
                    Text(date, fontSize = 11.sp, color = Color.White.copy(0.85f))
                }
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable { expandNotifications(ctx) },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, "Notifications", tint = Color.White,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(nav))
    }
}

@Composable
fun Desktop() {
    val ctx = LocalContext.current
    remember { Session.init(ctx) }
    val tick = Session.resumeTick.intValue
    val navPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    SideEffect { Session.navInset = navPx }

    var showSetup by remember { mutableStateOf(Req.needed(ctx)) }
    val force = Session.forceSetup.value
    LaunchedEffect(force) {
        if (force) { showSetup = true; Session.forceSetup.value = false }
    }
    // Detect every open app (needs Usage Access, optional)
    LaunchedEffect(tick) {
        while (true) {
            if (UsageTracker.granted(ctx)) {
                val running = withContext(Dispatchers.IO) {
                    try { UsageTracker.poll(ctx) } catch (_: Throwable) { null }
                }
                if (running != null) syncRunning(running)
            }
            delay(2500)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AccentColor)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f))) {
            if (showSetup) SetupScreen { showSetup = false }
            else Box(Modifier.align(Alignment.BottomCenter)) { Taskbar() }
        }
    }
}
