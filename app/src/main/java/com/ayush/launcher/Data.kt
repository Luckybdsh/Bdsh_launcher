package com.ayush.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

val CATS = listOf("Social", "Media", "Games", "Work", "Tools", "Other")

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val cat: String)

sealed class HomeEntry {
    data class App(val pkg: String) : HomeEntry()
    data class Folder(val id: String, val name: String, val pkgs: List<String>) : HomeEntry()
}

// Action ids: 0 None, 1 App drawer, 2 Notifications, 3 Quick settings, 4 Launcher settings
data class LauncherSettings(
    val theme: Int = 0, // 0 system, 1 light, 2 dark
    val swipeUp: Int = 1,
    val swipeDown: Int = 2,
    val doubleTap: Int = 3,
    val iconSize: Float = 56f,
    val columns: Int = 4,
    val showClock: Boolean = true,
    val use24h: Boolean = true
)

private val SOCIAL = listOf(
    "whatsapp", "telegram", "instagram", "facebook", "snapchat", "twitter", "discord",
    "reddit", "linkedin", "messenger", "signal", "pinterest", "tiktok"
)
private val GAMES = listOf(
    "game", "supercell", "pubg", "garena", "activision", "roblox", "minecraft",
    "tencent", "mojang", "miniclip", "ludo"
)
private val MEDIA = listOf(
    "youtube", "spotify", "netflix", "music", "video", "camera", "gallery",
    "photo", "player", "podcast", "hotstar"
)
private val WORK = listOf(
    "docs", "sheets", "slides", "gmail", "mail", "office", "calendar", "notion",
    "slack", "teams", "zoom", "outlook", "drive"
)
private val SYSTEM = listOf(
    "com.android.", "com.google.android.", "com.samsung.", "com.miui.", "com.oneplus.", "com.sec."
)

fun categorize(pkg: String, cat: Int): String {
    val p = pkg.lowercase()
    return when {
        cat == ApplicationInfo.CATEGORY_SOCIAL || SOCIAL.any { p.contains(it) } -> "Social"
        cat == ApplicationInfo.CATEGORY_GAME || GAMES.any { p.contains(it) } -> "Games"
        cat == ApplicationInfo.CATEGORY_AUDIO || cat == ApplicationInfo.CATEGORY_VIDEO ||
            cat == ApplicationInfo.CATEGORY_IMAGE || cat == ApplicationInfo.CATEGORY_NEWS ||
            MEDIA.any { p.contains(it) } -> "Media"
        cat == ApplicationInfo.CATEGORY_PRODUCTIVITY || cat == ApplicationInfo.CATEGORY_MAPS ||
            WORK.any { p.contains(it) } -> "Work"
        SYSTEM.any { p.startsWith(it) } -> "Tools"
        else -> "Other"
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                categorize(it.activityInfo.packageName, it.activityInfo.applicationInfo.category)
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun expandPanel(ctx: Context, quick: Boolean) {
    try {
        val sb = ctx.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod(if (quick) "expandSettingsPanel" else "expandNotificationsPanel").invoke(sb)
    } catch (_: Throwable) {
    }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun getList(k: String): List<String> =
        (p.getString(k, "") ?: "").split(",").filter { it.isNotEmpty() }

    fun putList(k: String, v: List<String>) {
        p.edit().putString(k, v.joinToString(",")).apply()
    }

    fun loadSettings() = LauncherSettings(
        theme = p.getInt("theme", 0),
        swipeUp = p.getInt("swipeUp", 1),
        swipeDown = p.getInt("swipeDown", 2),
        doubleTap = p.getInt("doubleTap", 3),
        iconSize = p.getFloat("iconSize", 56f),
        columns = p.getInt("columns", 4),
        showClock = p.getBoolean("showClock", true),
        use24h = p.getBoolean("use24h", true)
    )

    fun saveSettings(s: LauncherSettings) {
        p.edit()
            .putInt("theme", s.theme)
            .putInt("swipeUp", s.swipeUp)
            .putInt("swipeDown", s.swipeDown)
            .putInt("doubleTap", s.doubleTap)
            .putFloat("iconSize", s.iconSize)
            .putInt("columns", s.columns)
            .putBoolean("showClock", s.showClock)
            .putBoolean("use24h", s.use24h)
            .apply()
    }

    fun loadHome(): List<HomeEntry> {
        val out = mutableListOf<HomeEntry>()
        try {
            val arr = JSONArray(p.getString("home", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.getString("t") == "app") {
                    out.add(HomeEntry.App(o.getString("p")))
                } else {
                    val a = o.getJSONArray("a")
                    out.add(
                        HomeEntry.Folder(
                            o.getString("id"),
                            o.getString("n"),
                            (0 until a.length()).map { a.getString(it) }
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveHome(list: List<HomeEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            val o = JSONObject()
            when (e) {
                is HomeEntry.App -> {
                    o.put("t", "app")
                    o.put("p", e.pkg)
                }
                is HomeEntry.Folder -> {
                    o.put("t", "folder")
                    o.put("id", e.id)
                    o.put("n", e.name)
                    o.put("a", JSONArray(e.pkgs))
                }
            }
            arr.put(o)
        }
        p.edit().putString("home", arr.toString()).apply()
    }
}

class LauncherState(val ctx: Context) {
    private val store = Store(ctx)

    var apps by mutableStateOf<List<AppItem>>(emptyList())
    var settings by mutableStateOf(store.loadSettings())
    var dock by mutableStateOf(store.getList("dock"))
    var hidden by mutableStateOf(store.getList("hidden"))
    var home by mutableStateOf(store.loadHome())
    var tick by mutableIntStateOf(0)

    var drawer by mutableStateOf(false)
    var query by mutableStateOf("")
    var showHidden by mutableStateOf(false)
    var menu by mutableStateOf<AppItem?>(null)
    var menuSource by mutableStateOf("drawer")
    var showSettings by mutableStateOf(false)
    var openFolderId by mutableStateOf<String?>(null)
    var folderMenuId by mutableStateOf<String?>(null)
    var pickFolderFor by mutableStateOf<AppItem?>(null)

    fun app(pkg: String): AppItem? = apps.firstOrNull { it.pkg == pkg }

    fun folder(id: String): HomeEntry.Folder? =
        home.filterIsInstance<HomeEntry.Folder>().firstOrNull { it.id == id }

    fun updateSettings(s: LauncherSettings) {
        settings = s
        store.saveSettings(s)
    }

    fun closeDrawer() {
        drawer = false
        query = ""
        showHidden = false
    }

    fun closeAll() {
        closeDrawer()
        menu = null
        showSettings = false
        openFolderId = null
        folderMenuId = null
        pickFolderFor = null
    }

    fun launchApp(pkg: String) {
        try {
            ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
        } catch (_: Exception) {
        }
    }

    fun openAppInfo(pkg: String) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$pkg"))
            )
        } catch (_: Exception) {
        }
    }

    fun uninstall(pkg: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_DELETE).setData(Uri.parse("package:$pkg")))
        } catch (_: Exception) {
        }
    }

    fun runAction(a: Int) {
        when (a) {
            1 -> drawer = true
            2 -> expandPanel(ctx, false)
            3 -> expandPanel(ctx, true)
            4 -> showSettings = true
        }
    }

    fun toggleDock(pkg: String) {
        dock = if (pkg in dock) dock - pkg else if (dock.size < 5) dock + pkg else dock
        store.putList("dock", dock)
    }

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        store.putList("hidden", hidden)
    }

    private fun setHome(l: List<HomeEntry>) {
        home = l
        store.saveHome(l)
    }

    fun isOnHome(pkg: String) = home.any { it is HomeEntry.App && it.pkg == pkg }

    fun addToHome(pkg: String) {
        if (!isOnHome(pkg)) setHome(home + HomeEntry.App(pkg))
    }

    fun removeFromHome(pkg: String) {
        setHome(home.filterNot { it is HomeEntry.App && it.pkg == pkg })
    }

    fun createFolder(name: String, pkg: String) {
        val clean = name.ifBlank { "Folder" }
        setHome(
            home.filterNot { it is HomeEntry.App && it.pkg == pkg } +
                HomeEntry.Folder(UUID.randomUUID().toString(), clean, listOf(pkg))
        )
    }

    fun addToFolder(id: String, pkg: String) {
        setHome(
            home.filterNot { it is HomeEntry.App && it.pkg == pkg }.map {
                if (it is HomeEntry.Folder && it.id == id && pkg !in it.pkgs)
                    it.copy(pkgs = it.pkgs + pkg) else it
            }
        )
    }

    fun removeFromFolder(id: String, pkg: String) {
        setHome(
            home.mapNotNull { e ->
                if (e is HomeEntry.Folder && e.id == id) {
                    val rest = e.pkgs - pkg
                    if (rest.isEmpty()) null else e.copy(pkgs = rest)
                } else e
            }
        )
        if (folder(id) == null) openFolderId = null
    }

    fun renameFolder(id: String, name: String) {
        setHome(home.map { if (it is HomeEntry.Folder && it.id == id) it.copy(name = name) else it })
    }

    fun deleteFolder(id: String) {
        setHome(home.filterNot { it is HomeEntry.Folder && it.id == id })
        if (openFolderId == id) openFolderId = null
    }
}
