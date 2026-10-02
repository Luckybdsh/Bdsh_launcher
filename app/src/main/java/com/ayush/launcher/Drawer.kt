@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.ayush.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Closes the drawer when the user pulls down while a list is already at the top. */
class PullToClose(private val onClose: () -> Unit) : NestedScrollConnection {
    private var pull = 0f

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        pull = if (available.y > 0f) pull + available.y else 0f
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (pull > 180f) onClose()
        pull = 0f
        return Velocity.Zero
    }
}

@Composable
fun AppDrawer(state: LauncherState) {
    val fg = MaterialTheme.colorScheme.onSurface
    val visible = state.apps.filter {
        (it.pkg in state.hidden) == state.showHidden &&
            it.label.contains(state.query, ignoreCase = true)
    }
    val tabs = listOf("All") + CATS.filter { c -> visible.any { it.cat == c } }
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val conn = remember { PullToClose { state.closeDrawer() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            .pointerInput(Unit) {}
            .systemBarsPadding()
            .imePadding()
            .nestedScroll(conn)
    ) {
        // Drag handle: pull down to close
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(Unit) {
                    var t = 0f
                    detectVerticalDragGestures(
                        onDragStart = { t = 0f },
                        onDragEnd = { if (t > 60f) state.closeDrawer() }
                    ) { _, d -> t += d }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.3f)))
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                visible.firstOrNull()?.let { state.launchApp(it.pkg); state.closeDrawer() }
            })
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (state.showHidden) "Hidden apps" else "${visible.size} apps",
                color = fg.copy(alpha = 0.6f), fontSize = 13.sp
            )
            TextButton(onClick = { state.showHidden = !state.showHidden }) {
                Text(if (state.showHidden) "Show all" else "Hidden (${state.hidden.size})")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = pager.currentPage.coerceIn(0, tabs.lastIndex),
            containerColor = Color.Transparent,
            edgePadding = 12.dp,
            divider = {}
        ) {
            tabs.forEachIndexed { i, name ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = { Text(name) }
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            key = { tabs.getOrNull(it) ?: "page$it" }
        ) { page ->
            val name = tabs.getOrNull(page) ?: "All"
            val list = if (name == "All") visible else visible.filter { it.cat == name }
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.settings.columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.pkg }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        IconTile(
                            app.label, fg,
                            onClick = { state.launchApp(app.pkg); state.closeDrawer() },
                            onLong = { state.menuSource = "drawer"; state.menu = app }
                        ) { Image(app.icon, app.label, Modifier.size(state.settings.iconSize.dp)) }
                    }
                }
            }
        }
    }
}
