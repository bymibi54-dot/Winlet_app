package app.winlet.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reads the system state Winlet depends on. All checks are best-effort. */
object Req {
    private fun flag(ctx: Context, key: String) = Settings.Global.getInt(ctx.contentResolver, key, 0) == 1
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("desk", 0)

    fun devOptions(ctx: Context) = flag(ctx, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
    fun freeform(ctx: Context) = flag(ctx, "enable_freeform_support")
    fun resizable(ctx: Context) = flag(ctx, "force_resizable_activities")

    fun isHome(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    /** True only if the user ran: adb shell pm grant app.winlet.launcher android.permission.WRITE_SECURE_SETTINGS */
    fun canWriteSecure(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enableAuto(ctx: Context): Boolean = try {
        Settings.Global.putInt(ctx.contentResolver, "enable_freeform_support", 1)
        Settings.Global.putInt(ctx.contentResolver, "force_resizable_activities", 1)
        true
    } catch (_: Throwable) { false }

    fun requiredOk(ctx: Context) = devOptions(ctx) && freeform(ctx) && resizable(ctx) && isHome(ctx)
    fun needed(ctx: Context) = !prefs(ctx).getBoolean("skip_setup", false) && !requiredOk(ctx)
    fun skip(ctx: Context) = prefs(ctx).edit().putBoolean("skip_setup", true).apply()
}

@Composable
fun StepRow(ok: Boolean, title: String, desc: String, action: String?, optional: Boolean = false,
            onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
            tint = if (ok) Color(0xFF4ADE80) else Color.White.copy(0.5f), modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title + if (optional) "  (recommended)" else "", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = Color.White.copy(0.65f), fontSize = 12.sp)
            if (!ok && action != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                    Text(action, color = AccentColor)
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val tick = Session.resumeTick.intValue   // re-check every time the user comes back
    val dev = remember(tick) { Req.devOptions(ctx) }
    val ff = remember(tick) { Req.freeform(ctx) }
    val rs = remember(tick) { Req.resizable(ctx) }
    val home = remember(tick) { Req.isHome(ctx) }
    val usage = remember(tick) { UsageTracker.granted(ctx) }
    val secure = remember(tick) { Req.canWriteSecure(ctx) }
    val allOk = dev && ff && rs && home

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Session.resumeTick.intValue++
    }
    fun go(action: String) {
        try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
    }
    fun requestHome() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager
                homeLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else go(Settings.ACTION_HOME_SETTINGS)
        } catch (_: Throwable) { go(Settings.ACTION_HOME_SETTINGS) }
    }
    fun autoEnable() { Req.enableAuto(ctx); Session.resumeTick.intValue++ }

    Box(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(24.dp)).background(GlassColor)
                .border(1.dp, Color.White.copy(0.08f), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WinLogo(Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("Welcome to Winlet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text("A few switches are needed so apps can open in floating windows.",
                color = Color.White.copy(0.65f), fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            StepRow(dev, "Developer options",
                if (dev) "Enabled" else "Settings > About phone > Software information > tap Build number 7 times.",
                "Open About phone") { go(Settings.ACTION_DEVICE_INFO_SETTINGS) }
            StepRow(ff, "Freeform windows",
                if (ff) "Enabled" else "Developer options > Enable freeform windows, then reboot.",
                if (secure) "Enable automatically" else "Open Developer options") {
                if (secure) autoEnable() else go(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            }
            StepRow(rs, "Resizable activities",
                if (rs) "Enabled" else "Developer options > Force activities to be resizable, then reboot.",
                if (secure) "Enable automatically" else "Open Developer options") {
                if (secure) autoEnable() else go(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            }
            StepRow(home, "Default launcher",
                if (home) "Winlet is your Home app" else "Set Winlet as your Home app.",
                "Set as default") { requestHome() }
            if (UsageTracker.supported()) {
                StepRow(usage, "Usage access",
                    if (usage) "Enabled" else "Lets the taskbar show every open app, not only ones started from Winlet.",
                    "Grant access", optional = true) { go(Settings.ACTION_USAGE_ACCESS_SETTINGS) }
            }

            if (!secure && !(ff && rs)) {
                Spacer(Modifier.height(6.dp))
                Text("Advanced: run once from a computer to let Winlet flip the switches for you:\n" +
                    "adb shell pm grant app.winlet.launcher android.permission.WRITE_SECURE_SETTINGS",
                    color = Color.White.copy(0.5f), fontSize = 11.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                if (!allOk) {
                    TextButton(onClick = { Req.skip(ctx); onDone() }) {
                        Text("Skip for now", color = Color.White.copy(0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(
                    onClick = onDone, enabled = allOk,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color(0xFF0F172A))
                ) { Text("Continue") }
            }
        }
    }
}
