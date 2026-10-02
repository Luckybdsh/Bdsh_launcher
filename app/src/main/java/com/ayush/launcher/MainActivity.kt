@file:OptIn(ExperimentalFoundationApi::class)

package com.ayush.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp(tick.intValue) }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun getList(k: String): List<String> =
        (p.getString(k, "") ?: "").split(",").filter { it.isNotEmpty() }

    fun putList(k: String, v: List<String>) =
        p.edit().putString(k, v.joinToString(",")).apply()
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
                it.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun launch(ctx: Context, pkg: String) {
    try {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
    } catch (_: Exception) {
    }
}

@Composable
fun LauncherApp(tick: Int) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var dock by remember { mutableStateOf(store.getList("dock")) }
    var hidden by remember { mutableStateOf(store.getList("hidden")) }
    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<AppItem?>(null) }
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    BackHandler(drawer) { drawer = false; query = "" }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -120f) drawer = true }
                    ) { _, d -> total += d }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        try {
                            ctx.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
                            )
                        } catch (_: Exception) {
                        }
                    })
                }
                .systemBarsPadding()
        ) {
            // Clock
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 56.dp)
            ) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                    color = Color.White, fontSize = 72.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light
                )
                Text(
                    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                    color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp
                )
                if (dock.isEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Swipe up for apps.\nLong-press an app to pin it to the dock.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp
                    )
                }
            }

            // Dock
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dock.mapNotNull { p -> apps.find { it.pkg == p } }.forEach { app ->
                    Image(
                        app.icon, app.label,
                        Modifier
                            .size(52.dp)
                            .combinedClickable(
                                onClick = { launch(ctx, app.pkg) },
                                onLongClick = { menu = app }
                            )
                    )
                }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .combinedClickable(onClick = { drawer = true }),
                    contentAlignment = Alignment.Center
                ) { Text("⁝⁝⁝", color = Color.White, fontSize = 22.sp) }
            }
        }

        // Drawer
        AnimatedVisibility(
            visible = drawer,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            val list = apps.filter {
                (it.pkg in hidden) == showHidden && it.label.contains(query, ignoreCase = true)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xF2101018))
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        list.firstOrNull()?.let { launch(ctx, it.pkg); drawer = false; query = "" }
                    })
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showHidden) "Hidden apps" else "${list.size} apps",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                    )
                    TextButton(onClick = { showHidden = !showHidden }) {
                        Text(if (showHidden) "Show all" else "Hidden (${hidden.size})")
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(list, key = { it.pkg }) { app ->
                        Column(
                            Modifier
                                .padding(vertical = 8.dp)
                                .combinedClickable(
                                    onClick = { launch(ctx, app.pkg); drawer = false; query = "" },
                                    onLongClick = { menu = app }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(app.icon, app.label, Modifier.size(52.dp))
                            Text(
                                app.label, color = Color.White, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Long-press menu
        menu?.let { app ->
            val pinned = app.pkg in dock
            val isHidden = app.pkg in hidden
            AlertDialog(
                onDismissRequest = { menu = null },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { menu = null }) { Text("Close") } },
                title = { Text(app.label) },
                text = {
                    Column {
                        if (pinned || dock.size < 5) {
                            TextButton(onClick = {
                                dock = if (pinned) dock - app.pkg else dock + app.pkg
                                store.putList("dock", dock); menu = null
                            }) { Text(if (pinned) "Remove from dock" else "Pin to dock") }
                        }
                        TextButton(onClick = {
                            hidden = if (isHidden) hidden - app.pkg else hidden + app.pkg
                            store.putList("hidden", hidden); menu = null
                        }) { Text(if (isHidden) "Unhide app" else "Hide app") }
                        TextButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${app.pkg}"))
                            ); menu = null
                        }) { Text("App info") }
                        TextButton(onClick = {
                            ctx.startActivity(
                                Intent(Intent.ACTION_DELETE).setData(Uri.parse("package:${app.pkg}"))
                            ); menu = null
                        }) { Text("Uninstall") }
                    }
                }
            )
        }
    }
}
