@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ayush.launcher

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun IconTile(
    label: String,
    fg: Color,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    content: @Composable () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onClick = onClick,
                onLongClick = onLong
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
        if (showLabel) {
            Text(
                label, color = fg, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 84.dp)
            )
        }
    }
}

@Composable
fun FolderGlyph(state: LauncherState, f: HomeEntry.Folder, size: Dp) {
    val mini = size * 0.38f
    val gap = size * 0.06f
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            f.pkgs.take(4).mapNotNull { state.app(it) }.chunked(2).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    r.forEach { a -> Image(a.icon, null, Modifier.size(mini)) }
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
fun AppMenu(state: LauncherState, app: AppItem) {
    fun close() { state.menu = null }
    val src = state.menuSource
    AlertDialog(
        onDismissRequest = { close() },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { close() }) { Text("Close") } },
        title = { Text(app.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (src.startsWith("folder:")) {
                    MenuItem("Remove from folder") {
                        state.removeFromFolder(src.removePrefix("folder:"), app.pkg); close()
                    }
                }
                if (src == "home") {
                    MenuItem("Remove from home") { state.removeFromHome(app.pkg); close() }
                }
                MenuItem(if (app.pkg in state.dock) "Remove from dock" else "Pin to dock") {
                    state.toggleDock(app.pkg); close()
                }
                if (!state.isOnHome(app.pkg)) {
                    MenuItem("Add to home screen") { state.addToHome(app.pkg); close() }
                }
                MenuItem("Add to folder…") { close(); state.pickFolderFor = app }
                MenuItem(if (app.pkg in state.hidden) "Unhide app" else "Hide app") {
                    state.toggleHidden(app.pkg); close()
                }
                MenuItem("App info") { state.openAppInfo(app.pkg); close() }
                MenuItem("Uninstall") { state.uninstall(app.pkg); close() }
            }
        }
    )
}

@Composable
fun FolderMenu(state: LauncherState, f: HomeEntry.Folder) {
    AlertDialog(
        onDismissRequest = { state.folderMenuId = null },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { state.folderMenuId = null }) { Text("Close") } },
        title = { Text(f.name) },
        text = {
            Column {
                MenuItem("Open / rename") { state.folderMenuId = null; state.openFolderId = f.id }
                MenuItem("Delete folder") { state.deleteFolder(f.id); state.folderMenuId = null }
            }
        }
    )
}

@Composable
fun FolderDialog(state: LauncherState, f: HomeEntry.Folder) {
    val fg = MaterialTheme.colorScheme.onSurface
    Dialog(onDismissRequest = { state.openFolderId = null }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = f.name,
                    onValueChange = { state.renameFolder(f.id, it) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    f.pkgs.mapNotNull { state.app(it) }.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    IconTile(
                                        app.label, fg,
                                        onClick = { state.openFolderId = null; state.launchApp(app.pkg) },
                                        onLong = { state.menuSource = "folder:${f.id}"; state.menu = app }
                                    ) { Image(app.icon, app.label, Modifier.size(state.settings.iconSize.dp)) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderPicker(state: LauncherState, app: AppItem) {
    var name by remember { mutableStateOf("") }
    val folders = state.home.filterIsInstance<HomeEntry.Folder>()
    AlertDialog(
        onDismissRequest = { state.pickFolderFor = null },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { state.pickFolderFor = null }) { Text("Cancel") } },
        title = { Text("Add ${app.label} to folder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                folders.forEach { f ->
                    MenuItem("Add to ${f.name}") { state.addToFolder(f.id, app.pkg); state.pickFolderFor = null }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { state.createFolder(name, app.pkg); state.pickFolderFor = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Create new folder") }
            }
        }
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsDialog(state: LauncherState) {
    val ctx = LocalContext.current
    val s = state.settings
    val actions = listOf("None", "App drawer", "Notifications", "Quick settings", "Launcher settings")

    Dialog(
        onDismissRequest = { state.showSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Launcher settings", style = MaterialTheme.typography.titleLarge)

                SectionTitle("Theme")
                ChipRow(listOf("System", "Light", "Dark"), s.theme) { state.updateSettings(s.copy(theme = it)) }

                SectionTitle("Swipe up on home")
                ChipRow(actions, s.swipeUp) { state.updateSettings(s.copy(swipeUp = it)) }

                SectionTitle("Swipe down on home")
                ChipRow(actions, s.swipeDown) { state.updateSettings(s.copy(swipeDown = it)) }

                SectionTitle("Double tap on home")
                ChipRow(actions, s.doubleTap) { state.updateSettings(s.copy(doubleTap = it)) }

                SectionTitle("Icon size: ${s.iconSize.toInt()} dp")
                Slider(
                    value = s.iconSize,
                    onValueChange = { state.updateSettings(s.copy(iconSize = it)) },
                    valueRange = 40f..72f
                )

                SectionTitle("Drawer columns")
                ChipRow(listOf("3", "4", "5"), s.columns - 3) { state.updateSettings(s.copy(columns = it + 3)) }

                SwitchRow("Show clock", s.showClock) { state.updateSettings(s.copy(showClock = it)) }
                SwitchRow("24-hour clock", s.use24h) { state.updateSettings(s.copy(use24h = it)) }

                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change wallpaper") }

                OutlinedButton(
                    onClick = {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set as default launcher") }

                Button(onClick = { state.showSettings = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}
