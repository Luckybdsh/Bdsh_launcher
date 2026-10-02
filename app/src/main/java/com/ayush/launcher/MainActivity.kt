package com.ayush.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(this)
        addOnNewIntentListener { state.closeAll() }
        setContent { LauncherRoot(state) }
    }

    override fun onResume() {
        super.onResume()
        state.tick++
    }
}

@Composable
fun LauncherRoot(state: LauncherState) {
    val activity = LocalContext.current as ComponentActivity
    val dark = when (state.settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        activity.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t),
            navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        )
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        LauncherScreen(state)
    }
}

@Composable
fun ClockBlock(use24h: Boolean, fg: Color, modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    Column(modifier) {
        Text(
            SimpleDateFormat(pattern, Locale.getDefault()).format(now),
            color = fg, fontSize = 72.sp, fontWeight = FontWeight.Light
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
            color = fg.copy(alpha = 0.8f), fontSize = 18.sp
        )
    }
}

@Composable
fun HomeCell(state: LauncherState, entry: HomeEntry, fg: Color) {
    val size = state.settings.iconSize.dp
    when (entry) {
        is HomeEntry.App -> {
            val app = state.app(entry.pkg)
            if (app != null) {
                IconTile(
                    app.label, fg,
                    onClick = { state.launchApp(app.pkg) },
                    onLong = { state.menuSource = "home"; state.menu = app }
                ) { Image(app.icon, app.label, Modifier.size(size)) }
            }
        }
        is HomeEntry.Folder -> {
            IconTile(
                entry.name, fg,
                onClick = { state.openFolderId = entry.id },
                onLong = { state.folderMenuId = entry.id }
            ) { FolderGlyph(state, entry, size) }
        }
    }
}

@Composable
fun LauncherScreen(state: LauncherState) {
    val ctx = LocalContext.current
    val s = state.settings
    val fg = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(state.tick) {
        state.apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    BackHandler(state.drawer) { state.closeDrawer() }

    val homeAlpha by animateFloatAsState(if (state.drawer) 0f else 1f, tween(250), label = "homeAlpha")
    val homeScale by animateFloatAsState(if (state.drawer) 0.94f else 1f, tween(300), label = "homeScale")

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.28f))
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -110f) state.runAction(state.settings.swipeUp)
                            else if (total > 110f) state.runAction(state.settings.swipeDown)
                        }
                    ) { _, d -> total += d }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { state.runAction(state.settings.doubleTap) },
                        onLongPress = { state.showSettings = true }
                    )
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .graphicsLayer { alpha = homeAlpha; scaleX = homeScale; scaleY = homeScale }
            ) {
                if (s.showClock) {
                    ClockBlock(s.use24h, fg, Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 40.dp))
                }
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = if (s.showClock) 200.dp else 48.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    if (state.home.isEmpty()) {
                        Text(
                            "Swipe up for apps.\nLong-press an app → Add to home screen or folder.\nLong-press here for settings.",
                            color = fg.copy(alpha = 0.6f), fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    state.home.take(16).chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { entry ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    HomeCell(state, entry, fg)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // Dock
                val dockSize = minOf(s.iconSize, 48f).dp
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(fg.copy(alpha = 0.14f))
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.dock.mapNotNull { state.app(it) }.forEach { app ->
                        IconTile(
                            app.label, fg, showLabel = false,
                            onClick = { state.launchApp(app.pkg) },
                            onLong = { state.menuSource = "dock"; state.menu = app }
                        ) { Image(app.icon, app.label, Modifier.size(dockSize)) }
                    }
                    Box(
                        Modifier
                            .size(dockSize)
                            .clip(CircleShape)
                            .background(fg.copy(alpha = 0.2f))
                            .clickable { state.drawer = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(3) { Box(Modifier.size(5.dp).clip(CircleShape).background(fg)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.drawer,
            enter = slideInVertically(spring(stiffness = 300f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut()
        ) { AppDrawer(state) }

        state.menu?.let { AppMenu(state, it) }
        state.folderMenuId?.let { id -> state.folder(id)?.let { FolderMenu(state, it) } }
        state.openFolderId?.let { id -> state.folder(id)?.let { FolderDialog(state, it) } }
        state.pickFolderFor?.let { FolderPicker(state, it) }
        if (state.showSettings) SettingsDialog(state)
    }
}
