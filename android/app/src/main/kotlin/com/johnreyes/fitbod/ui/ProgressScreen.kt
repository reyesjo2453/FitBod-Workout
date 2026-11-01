package com.johnreyes.fitbod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.core.Analytics
import com.johnreyes.fitbod.core.SyncDoc
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(repo: Repository, spec: LayoutSpec) {
    val doc by repo.doc.collectAsState()
    val exercises = Analytics.trainedExercises(doc)
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val unit = doc.settings.units

    val list: @Composable () -> Unit = {
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ScreenTitle("Progress", "Strength & volume") }
            item { Overview(doc) }
            item { SectionLabel("Exercises · estimated 1RM") }
            if (exercises.isEmpty()) item { EmptyState("No lifts logged yet", "Finish a workout to start tracking PRs.") }
            items(exercises, key = { it }) { name ->
                val h = Analytics.history(doc, name)
                val r = Analytics.records(doc, name)
                Panel(
                    color = if (spec.wide && open == name) Palette.CardHi else Palette.Card,
                    onClick = { open = name }, padding = PaddingValues(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseThumb(name, 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Best ${w(r.heaviest?.weight)} $unit × ${r.heaviest?.reps ?: "–"} · ${r.sessions} sessions",
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                        }
                        Sparkline(h.takeLast(8).map { it.bestE1rm }, Modifier.size(width = 56.dp, height = 28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(w(Math.round(r.bestE1rm).toDouble()), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    val detail: @Composable () -> Unit = {
        val name = open
        if (name == null) EmptyState("Pick an exercise", "See its estimated 1RM trend, PRs and every logged set.", Modifier.padding(top = 80.dp))
        else ExerciseProgress(doc, name, unit, showBack = !spec.wide) { open = null }
    }

    if (spec.wide) TwoPane(spec, list, detail)
    else if (open != null) {
        BackHandler { open = null }
        detail()
    } else list()
}

@Composable
private fun Overview(doc: SyncDoc) {
    val t = Analytics.totals(doc, LocalDate.now())
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("workouts", "${t.workouts}", Modifier.weight(1f), Palette.Accent)
            StatTile("this week", "${t.thisWeek}", Modifier.weight(1f))
            StatTile("week streak", "${t.streakWeeks}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Weekly volume", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${bigNumber(t.volume)} ${doc.settings.units} total", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
                Spacer(Modifier.height(12.dp))
                val weeks = Analytics.weeklyVolume(doc, LocalDate.now(), 8)
                BarChart(weeks.map { it.second }, weeks.map { it.first.format(DateTimeFormatter.ofPattern("M/d")) })
            }
        }
    }
}

@Composable
private fun ExerciseProgress(doc: SyncDoc, name: String, unit: String, showBack: Boolean, onBack: () -> Unit) {
    val h = Analytics.history(doc, name)
    val r = Analytics.records(doc, name)
    val baseline = doc.exercises.firstOrNull { it.name.equals(name, true) }?.baseline
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Box(Modifier.weight(1f)) { ScreenTitle(name, com.johnreyes.fitbod.core.Muscles.primary(name)) }
            }
        }
        item {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Text("Estimated 1RM", style = MaterialTheme.typography.titleSmall)
                    Text("${w(Math.round(r.bestE1rm).toDouble())} $unit best", style = MaterialTheme.typography.headlineSmall, color = Palette.Accent)
                    Spacer(Modifier.height(12.dp))
                    LineChart(h.map { it.bestE1rm })
                    if (h.size >= 2) {
                        Row {
                            Text(h.first().date, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                            Text(h.last().date, style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("heaviest", "${w(r.heaviest?.weight)}×${r.heaviest?.reps ?: "–"}", Modifier.weight(1f))
                StatTile("best volume", bigNumber(r.bestVolume), Modifier.weight(1f))
                StatTile("sessions", "${r.sessions}", Modifier.weight(1f))
            }
        }
        if (!baseline.isNullOrBlank()) {
            item {
                Text("Baseline: $baseline", style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { SectionLabel("History") }
        items(h.reversed()) { s ->
            Panel(Modifier.fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Column {
                    Row {
                        Text(s.date, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(s.gym ?: "", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.sets.joinToString("   ") { "${w(it.weight)}×${it.reps}" },
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                    )
                }
            }
        }
    }
}
