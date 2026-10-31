package com.johnreyes.fitbod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    WORKOUT("Workout", Glyphs.Dumbbell),
    LOG("Log", Glyphs.Calendar),
    PROGRESS("Progress", Glyphs.Bars),
    SETTINGS("Settings", Icons.Default.Settings),
}

/**
 * Layout decisions for the current window.
 *
 * Folded (cover screen, compact width) gets one pane and a bottom bar. Unfolded
 * (medium/expanded width) gets a navigation rail and list/detail panes. Half-open
 * postures move pane splits onto the hinge.
 */
data class LayoutSpec(val wide: Boolean, val split: Float = 0.42f, val posture: Posture = Posture.Flat) {
    val tabletop: Posture.Tabletop? get() = posture as? Posture.Tabletop
}

@Composable
fun FitbodRoot(
    repo: Repository,
    widthClass: WindowWidthSizeClass,
    posture: Posture,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val book = posture as? Posture.Book
    val spec = LayoutSpec(
        wide = widthClass != WindowWidthSizeClass.Compact || book != null,
        split = book?.hingeFraction ?: if (widthClass == WindowWidthSizeClass.Expanded) 0.4f else 0.46f,
        posture = posture,
    )
    var tab by rememberSaveable { mutableStateOf(Tab.WORKOUT) }
    var editingRoutine by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionOpen by rememberSaveable { mutableStateOf(false) }
    val session by repo.session.collectAsState()

    // One shared clock for timers.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(500) } }

    Surface(color = Palette.Bg, modifier = Modifier.fillMaxSize()) {
        when {
            sessionOpen && session != null -> {
                BackHandler { sessionOpen = false }
                Box(Modifier.safeDrawingPadding()) {
                    SessionScreen(repo, session!!, spec, now, onMinimize = { sessionOpen = false }, onClosed = { sessionOpen = false })
                }
            }
            editingRoutine != null -> {
                BackHandler { editingRoutine = null }
                Box(Modifier.safeDrawingPadding()) {
                    RoutineEditor(repo, editingRoutine!!, spec, onClose = { editingRoutine = null })
                }
            }
            else -> {
                BackHandler(enabled = tab != Tab.WORKOUT) { tab = Tab.WORKOUT }
                val content: @Composable (Modifier) -> Unit = { m ->
                    Column(m) {
                        if (session != null) ResumeBanner(session!!.title, now - session!!.startedAt) { sessionOpen = true }
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.WORKOUT -> WorkoutScreen(
                                    repo, spec,
                                    onStart = { sessionOpen = true },
                                    onEditRoutine = { editingRoutine = it },
                                )
                                Tab.LOG -> LogScreen(repo, spec)
                                Tab.PROGRESS -> ProgressScreen(repo, spec)
                                Tab.SETTINGS -> SettingsScreen(repo, spec, onSignIn, onSignOut, onEditRoutine = { editingRoutine = it })
                            }
                        }
                    }
                }
                if (spec.wide) {
                    Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                        NavigationRail(containerColor = Palette.Bg, modifier = Modifier.fillMaxHeight()) {
                            Spacer(Modifier.padding(top = 12.dp))
                            Tab.entries.forEach { t ->
                                NavigationRailItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = Palette.Accent, selectedTextColor = Palette.Accent,
                                        indicatorColor = Palette.AccentDim, unselectedIconColor = Palette.Muted,
                                        unselectedTextColor = Palette.Muted,
                                    ),
                                )
                            }
                        }
                        content(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        content(Modifier.weight(1f).fillMaxWidth())
                        NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Icon(t.icon, t.label) }, label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Palette.Accent, selectedTextColor = Palette.Accent,
                                        indicatorColor = Palette.AccentDim, unselectedIconColor = Palette.Muted,
                                        unselectedTextColor = Palette.Muted,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeBanner(title: String, elapsedMs: Long, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Palette.Accent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WORKOUT IN PROGRESS", style = MaterialTheme.typography.labelSmall)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(clock(elapsedMs / 1000), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text("Resume ›", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** List/detail panes for unfolded layouts. Uses the hinge position in book posture. */
@Composable
fun TwoPane(spec: LayoutSpec, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(spec.split).fillMaxHeight()) { left() }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Palette.Line))
        Box(Modifier.weight(1f - spec.split).fillMaxHeight()) { right() }
    }
}
