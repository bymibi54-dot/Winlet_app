package app.winlet.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class QuickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        setContent { QuickPanel { finish() } }
    }
    override fun onPause() { super.onPause(); if (!isFinishing) finish() }
}

@Composable
fun Tile(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) AccentColor.copy(0.35f) else Color.White.copy(0.08f))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = Color.White)
    }
}

@Composable
fun QuickPanel(done: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var torch by remember { mutableStateOf(Session.torch) }
    val bat = remember { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val level = bat?.let {
        it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    } ?: 0
    val st = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    val now = remember { Date() }

    fun go(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
        done()
    }
    fun toggleTorch() {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.first {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torch = !torch
            Session.torch = torch
            cm.setTorchMode(id, torch)
        } catch (_: Throwable) { torch = false; Session.torch = false }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AccentColor)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(GlassColor)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(now), fontSize = 28.sp, color = Color.White)
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                fontSize = 13.sp, color = Color.White.copy(0.65f))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (charging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("$level%" + if (charging) "  •  Charging" else "", color = Color.White, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Slider(
                    value = vol, valueRange = 0f..maxVol.toFloat(),
                    onValueChange = { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0) },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Tile(Icons.Default.Wifi, "Wi-Fi", false) {
                    go(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                        else Settings.ACTION_WIFI_SETTINGS))
                }
                Tile(Icons.Default.Bluetooth, "Bluetooth", false) { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                Tile(Icons.Default.FlashlightOn, "Torch", torch) { toggleTorch() }
                Tile(Icons.Default.Settings, "Settings", false) { go(Intent(Settings.ACTION_SETTINGS)) }
            }
            Spacer(Modifier.height(14.dp))
            MenuRow(Icons.Default.ClearAll, "Close all apps") { Session.closeAll(ctx); done() }
        }
    }
}
