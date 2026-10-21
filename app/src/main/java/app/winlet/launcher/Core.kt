package app.winlet.launcher

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

val GlassColor = Color(0xF2181B2B)
val AccentColor = Color(0xFF60A5FA)

data class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: ImageBitmap)

enum class Layout { FLOAT, LEFT, RIGHT, FULL }

object Session {
    val opened = mutableStateListOf<AppItem>()
    val pinned = mutableStateListOf<String>()
    var apps: List<AppItem> = emptyList()
    var counter = 0
    var torch = false
    var navInset = 0
    val resumeTick = mutableIntStateOf(0)
    val forceSetup = mutableStateOf(false)
    val launchedAt = HashMap<String, Long>()
    var homePkgs: Set<String> = emptySet()
    private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        ready = true
        apps = loadApps(ctx)
        homePkgs = ctx.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        val saved = ctx.getSharedPreferences("desk", 0).getString("pins", "") ?: ""
        pinned.addAll(saved.split("|").filter { p -> p.isNotEmpty() && apps.any { it.pkg == p } })
    }
    fun find(pkg: String) = apps.firstOrNull { it.pkg == pkg }
    fun togglePin(ctx: Context, pkg: String) {
        if (!pinned.remove(pkg)) pinned.add(pkg)
        ctx.getSharedPreferences("desk", 0).edit().putString("pins", pinned.joinToString("|")).apply()
    }
    fun close(ctx: Context, pkg: String) { kill(ctx, pkg); opened.removeAll { it.pkg == pkg } }
    fun closeAll(ctx: Context) { opened.forEach { kill(ctx, it.pkg) }; opened.clear() }
    private fun kill(ctx: Context, pkg: String) {
        try { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg) }
        catch (_: Throwable) { }
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(main, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            val i = pm.getLaunchIntentForPackage(it.activityInfo.packageName)
                ?: Intent(main).setClassName(it.activityInfo.packageName, it.activityInfo.name)
            AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, i,
                it.loadIcon(pm).toBitmap(128, 128).asImageBitmap())
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun freeform(rect: Rect): Bundle {
    val opts = ActivityOptions.makeBasic().setLaunchBounds(rect)
    try {
        ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
            .invoke(opts, 5) // WINDOWING_MODE_FREEFORM
    } catch (_: Throwable) { }
    return opts.toBundle()
}

private fun start(ctx: Context, intent: Intent, rect: Rect) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(intent, freeform(rect)) } catch (_: Throwable) { ctx.startActivity(intent) }
}

/** Opens an app in a floating window, or snapped to the left/right half, or full work area. */
fun launchApp(ctx: Context, app: AppItem, layout: Layout = Layout.FLOAT) {
    val dm = ctx.resources.displayMetrics
    val d = dm.density
    val top = (40 * d).toInt()
    val bottom = dm.heightPixels - taskbarReserve(ctx)
    val gap = (6 * d).toInt()
    val W = dm.widthPixels
    val rect = when (layout) {
        Layout.LEFT -> Rect(gap, top, W / 2 - gap / 2, bottom)
        Layout.RIGHT -> Rect(W / 2 + gap / 2, top, W - gap, bottom)
        Layout.FULL -> Rect(0, top, W, bottom)
        Layout.FLOAT -> {
            val w = (W * (if (W > dm.heightPixels) 0.62f else 0.9f)).toInt()
            val h = minOf((dm.heightPixels * 0.6f).toInt(), bottom - top)
            val step = ((Session.counter++ % 5) * 26 * d).toInt()
            val left = ((W - w) / 2 - 40 * d).toInt().coerceAtLeast(0) + step
            Rect(left, top + step, left + w, minOf(top + step + h, bottom))
        }
    }
    if (Session.opened.none { it.pkg == app.pkg }) Session.opened.add(app)
    Session.launchedAt[app.pkg] = System.currentTimeMillis()
    start(ctx, Intent(app.intent), rect)
}

/** Opens one of our own panels (Start / Quick / App menu) as a floating window above app windows. */
fun openPanel(ctx: Context, cls: Class<*>, wDp: Int, hDp: Int, right: Boolean, pkg: String? = null) {
    val dm = ctx.resources.displayMetrics
    val d = dm.density
    val w = minOf(dm.widthPixels - (24 * d).toInt(), (wDp * d).toInt())
    val h = minOf((dm.heightPixels * 0.72f).toInt(), (hDp * d).toInt())
    val bottom = dm.heightPixels - taskbarReserve(ctx)
    val left = if (right) dm.widthPixels - (12 * d).toInt() - w else (dm.widthPixels - w) / 2
    val intent = Intent(ctx, cls)
    if (pkg != null) intent.putExtra("pkg", pkg)
    start(ctx, intent, Rect(left, (bottom - h).coerceAtLeast(0), left + w, bottom))
}

/** Space at the bottom kept free for the taskbar (bar height + navigation inset + small gap). */
fun taskbarReserve(ctx: Context): Int = (55 * ctx.resources.displayMetrics.density).toInt() + Session.navInset

/** Detects which apps currently have a live activity, using Usage Access (Android 10+). */
object UsageTracker {
    private val live = HashMap<String, MutableSet<String>>()
    private var last = 0L

    fun supported() = Build.VERSION.SDK_INT >= 29

    fun granted(ctx: Context): Boolean {
        if (!supported()) return false
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Reads new events (call off the main thread) and returns packages with a live activity. */
    fun poll(ctx: Context): Set<String> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (last == 0L) now - 6 * 3_600_000L else last - 2_000L
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val cls = e.className ?: continue
            when (e.eventType) {
                1, 2 -> live.getOrPut(e.packageName) { HashSet() }.add(cls)   // resumed / paused
                23, 24 -> live[e.packageName]?.remove(cls)                    // stopped / destroyed
            }
        }
        last = now
        return live.filterValues { it.isNotEmpty() }.keys.toSet()
    }
}

/** Mirrors detected running apps into the taskbar list. */
fun syncRunning(running: Set<String>) {
    val now = System.currentTimeMillis()
    val visible = Session.apps.filter { it.pkg in running && it.pkg !in Session.homePkgs }
    val keep = visible.map { it.pkg }.toSet()
    Session.opened.removeAll { it.pkg !in keep && now - (Session.launchedAt[it.pkg] ?: 0L) > 6_000L }
    visible.forEach { a -> if (Session.opened.none { it.pkg == a.pkg }) Session.opened.add(a) }
}
