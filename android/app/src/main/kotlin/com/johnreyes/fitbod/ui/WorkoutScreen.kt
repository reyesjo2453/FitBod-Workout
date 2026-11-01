package com.johnreyes.fitbod.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.SessionFactory
import com.johnreyes.fitbod.core.Analytics
import com.johnreyes.fitbod.core.Edits
import com.johnreyes.fitbod.core.RoutineItem
import com.johnreyes.fitbod.core.SyncDoc
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun WorkoutScreen(repo: Repository, spec: LayoutSpec, onStart: () -> Unit, onEditRoutine: (String) -> Unit) {
    val doc by repo.doc.collectAsState()
    val session by repo.session.collectAsState()
    val routines = Edits.routineNames(doc)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val routine = picked?.takeIf { it in routines } ?: Analytics.nextRoutine(doc)
    val lastGym = Analytics.doneWorkouts(doc).lastOrNull { it.gym != null }?.gym
    var gymPick by rememberSaveable { mutableStateOf<String?>(null) }
    val gym = gymPick ?: lastGym ?: doc.gyms.firstOrNull()
    var newRoutine by remember { mutableStateOf(false) }

    val unit = doc.settings.units
    val items = routine?.let { Edits.routineItems(doc, it) }.orEmpty()

    val start: () -> Unit = {
        if (session == null && routine != null) {
            repo.updateSession { SessionFactory.fromRoutine(doc, routine, gym, System.currentTimeMillis()) }
        }
        onStart()
    }

    if (newRoutine) {
        TextPrompt("New workout", "Set ${'A' + routines.size} — Full Body", "Name", onDismiss = { newRoutine = false }) { name ->
            onEditRoutine(name)
        }
    }

    val header: @Composable () -> Unit = {
        Column {
            val today = LocalDate.now()
            ScreenTitle(
                title = routine ?: "No workouts yet",
                subtitle = if (picked == null) "Up next · " + today.format(DateTimeFormatter.ofPattern("EEE, MMM d")) else "Selected workout",
                trailing = {
                    if (routine != null) IconButton(onClick = { onEditRoutine(routine) }) { Icon(Icons.Default.Edit, "Edit workout", tint = Palette.Muted) }
                },
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                routines.forEach { r -> Chip(shortName(r), selected = r == routine) { picked = r } }
                Chip("+ New", selected = false) { newRoutine = true }
            }
            if (doc.gyms.isNotEmpty()) {
                SectionLabel("Gym")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    doc.gyms.forEach { g -> Chip(g, selected = g == gym) { gymPick = g } }
                }
            }
            Spacer(Modifier.height(16.dp))
            RoutineSummary(doc, routine, items)
        }
    }

    val startButton: @Composable (Modifier) -> Unit = { m ->
        PrimaryButton(
            text = when {
                session != null -> "Resume Workout"
                else -> "Start Workout"
            },
            enabled = session != null || items.isNotEmpty(),
            modifier = m.fillMaxWidth(),
            onClick = start,
        )
    }

    if (spec.wide) {
        TwoPane(
            spec,
            left = {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        header()
                        SectionLabel("This week")
                        WeekStats(doc)
                    }
                    startButton(Modifier.padding(vertical = 16.dp))
                }
            },
            right = {
                LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { SectionLabel("${items.size} exercises", Modifier.padding(bottom = 0.dp)) }
                    items(items, key = { it.exercise + it.sortOrder }) { ExerciseRow(doc, it, gym, unit) }
                    if (items.isEmpty()) item { EmptyState("Build this workout", "Tap the pencil to add exercises, sets, reps and weights.") }
                }
            },
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { header() }
                item { SectionLabel("${items.size} exercises", Modifier.padding(bottom = 0.dp)) }
                items(items, key = { it.exercise + it.sortOrder }) { ExerciseRow(doc, it, gym, unit) }
                if (items.isEmpty()) item { EmptyState("Build this workout", "Tap the pencil to add exercises, sets, reps and weights.") }
            }
            startButton(Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** "Set A — Full Body" → "Set A" for chips. */
fun shortName(routine: String): String = routine.substringBefore(" — ").substringBefore(" - ").ifBlank { routine }

@Composable
private fun RoutineSummary(doc: SyncDoc, routine: String?, items: List<RoutineItem>) {
    val sets = items.sumOf { it.targetSets }
    val minutes = items.sumOf { it.targetSets * (40 + (it.restSeconds ?: doc.settings.defaultRestSeconds)) } / 60
    val last = Analytics.doneWorkouts(doc).lastOrNull { it.title == routine }
    val ago = last?.let { Analytics.parse(it.date) }?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("min", "~$minutes", Modifier.weight(1f))
        StatTile("sets", "$sets", Modifier.weight(1f))
        StatTile(
            "last done", when (ago) { null -> "–"; 0L -> "Today"; 1L -> "1 day"; else -> "$ago days" },
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExerciseRow(doc: SyncDoc, item: RoutineItem, gym: String?, unit: String) {
    val p = Analytics.prescribe(doc, item, gym)
    Panel(padding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(item.exercise)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.exercise, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${p.sets} sets · ${p.reps} reps · ${w(p.weight)} $unit",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MuscleTag(item.exercise)
                    if (p.weight > (p.previous.maxOfOrNull { it.weight ?: 0.0 } ?: Double.MAX_VALUE)) {
                        Spacer(Modifier.width(6.dp))
                        Text("▲ ${w(p.weight)}", style = MaterialTheme.typography.labelSmall, color = Palette.Good)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStats(doc: SyncDoc) {
    val t = Analytics.totals(doc, LocalDate.now())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("workouts", "${t.thisWeek}", Modifier.weight(1f), accent = Palette.Accent)
        StatTile("week streak", "${t.streakWeeks}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Panel(Modifier.fillMaxWidth()) {
        Column {
            Text("Weekly volume (${doc.settings.units})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            val weeks = Analytics.weeklyVolume(doc, LocalDate.now(), 8)
            BarChart(weeks.map { it.second }, weeks.map { it.first.format(DateTimeFormatter.ofPattern("M/d")) })
        }
    }
}
