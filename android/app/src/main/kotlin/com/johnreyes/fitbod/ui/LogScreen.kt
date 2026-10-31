package com.johnreyes.fitbod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.core.Analytics
import com.johnreyes.fitbod.core.Edits
import com.johnreyes.fitbod.core.Workout
import java.time.format.DateTimeFormatter

@Composable
fun LogScreen(repo: Repository, spec: LayoutSpec) {
    val doc by repo.doc.collectAsState()
    val workouts = Analytics.doneWorkouts(doc).reversed()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = workouts.firstOrNull { it.syncId == openId }
    val unit = doc.settings.units
    var confirmDelete by remember { mutableStateOf<Workout?>(null) }

    confirmDelete?.let { wk ->
        ConfirmDialog(
            "Delete workout?", "${wk.title} on ${wk.date} will be removed here and from Drive.", "Delete",
            onDismiss = { confirmDelete = null },
        ) {
            repo.edit { Edits.deleteWorkout(it, wk.syncId, System.currentTimeMillis()) }
            openId = null
            repo.syncNow()
        }
    }

    val list: @Composable () -> Unit = {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenTitle("Log", "${workouts.size} workouts") }
            if (workouts.isEmpty()) item { EmptyState("No workouts yet", "Finished workouts and anything synced from Drive show up here.") }
            var month = ""
            workouts.forEach { wk ->
                val m = Analytics.parse(wk.date)?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: wk.date
                if (m != month) {
                    month = m
                    item(key = "m-$m") { SectionLabel(m) }
                }
                item(key = wk.syncId) { WorkoutCard(wk, unit, selected = spec.wide && wk.syncId == openId) { openId = wk.syncId } }
            }
        }
    }

    val detail: @Composable () -> Unit = {
        if (open == null) EmptyState("Pick a workout", "Its sets, volume and gym appear here.", Modifier.padding(top = 80.dp))
        else WorkoutDetail(open, unit, showBack = !spec.wide, onBack = { openId = null }, onDelete = { confirmDelete = open })
    }

    if (spec.wide) TwoPane(spec, list, detail)
    else if (open != null) {
        BackHandler { openId = null }
        detail()
    } else list()
}

@Composable
private fun WorkoutCard(wk: Workout, unit: String, selected: Boolean, onClick: () -> Unit) {
    val date = Analytics.parse(wk.date)
    Panel(color = if (selected) Palette.CardHi else Palette.Card, onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Palette.AccentDim),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(date?.format(DateTimeFormatter.ofPattern("MMM"))?.uppercase() ?: "", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
                Text(date?.dayOfMonth?.toString() ?: "?", style = MaterialTheme.typography.titleLarge, color = Palette.Text)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(wk.title.ifBlank { "Workout" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        wk.gym,
                        wk.durationMinutes?.let { "$it min" },
                        (wk.setCount ?: wk.sets.size.takeIf { it > 0 })?.let { "$it sets" },
                    ).joinToString(" · ").ifBlank { "Logged workout" },
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
            }
            wk.volume?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(bigNumber(it), style = MaterialTheme.typography.titleMedium)
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetail(wk: Workout, unit: String, showBack: Boolean, onBack: () -> Unit, onDelete: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Box(Modifier.weight(1f)) { ScreenTitle(wk.title.ifBlank { "Workout" }, wk.date + (wk.gym?.let { " · $it" } ?: "")) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete workout", tint = Palette.Muted) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("volume $unit", wk.volume?.let(::bigNumber) ?: "–", Modifier.weight(1f), Palette.Accent)
                StatTile("sets", (wk.setCount ?: wk.sets.size).toString(), Modifier.weight(1f))
                StatTile("minutes", wk.durationMinutes?.toString() ?: "–", Modifier.weight(1f))
            }
        }
        val byExercise = wk.sets.groupBy { it.exercise }
        if (byExercise.isEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Text("Summary only", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (wk.exerciseNames.isEmpty()) "This entry was imported without set details."
                            else wk.exerciseNames.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                        )
                    }
                }
            }
        }
        byExercise.forEach { (name, sets) ->
            item(key = name) {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseThumb(name, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            val best = sets.filter { it.weight != null && it.reps != null }.maxOfOrNull { Analytics.e1rm(it.weight!!, it.reps!!) }
                            if (best != null) Text("e1RM ${w(best)}", style = MaterialTheme.typography.labelMedium, color = Palette.Good)
                        }
                        Spacer(Modifier.height(10.dp))
                        sets.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text((s.supersetGroup ?: "") + "${s.setNumber}", color = Palette.Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                                Text("${w(s.weight)} $unit", modifier = Modifier.weight(1f))
                                Text("× ${s.reps ?: "–"}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
