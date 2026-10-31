package com.johnreyes.fitbod.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.core.Edits
import com.johnreyes.fitbod.core.RoutineItem

/**
 * Edits one routine (e.g. "Set A — Full Body"): its name, exercise order, and each
 * exercise's target sets, reps, weight and rest. Saved as `routines` rows in the sync file.
 */
@Composable
fun RoutineEditor(repo: Repository, routine: String, spec: LayoutSpec, onClose: () -> Unit) {
    val doc by repo.doc.collectAsState()
    val original = remember(routine) { Edits.routineItems(doc, routine) }
    val items = remember(routine) { mutableStateListOf<RoutineItem>().apply { addAll(original) } }
    var name by remember(routine) { mutableStateOf(routine) }
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val unit = doc.settings.units
    val step = if (doc.settings.isKg) 2.5 else 5.0
    val exists = original.isNotEmpty()

    if (picker) {
        ExercisePicker(doc.exercises.map { it.name }, onDismiss = { picker = false }) { ex ->
            val last = items.lastOrNull()
            items += RoutineItem(
                routine = name, exercise = ex, targetSets = last?.targetSets ?: 3, targetWeight = 0.0,
                targetReps = last?.targetReps ?: 10, restSeconds = null, sortOrder = items.size + 1,
            )
            picker = false
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete \"$routine\"?", "Your logged workouts stay in the Log.", "Delete", onDismiss = { confirmDelete = false }) {
            repo.edit { Edits.deleteRoutine(it, routine) }
            onClose()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
            Text(if (exists) "Edit workout" else "New workout", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (exists) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete workout", tint = Palette.Muted) }
            TextButton(
                enabled = name.isNotBlank() && items.isNotEmpty(),
                onClick = {
                    repo.edit { Edits.saveRoutine(it, routine, items.toList(), name.trim()) }
                    onClose()
                },
            ) { Text("Save", color = Palette.Accent, style = MaterialTheme.typography.labelLarge) }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = if (spec.wide) 720.dp else 10_000.dp),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Workout name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                itemsIndexed(items) { i, it ->
                    fun set(f: (RoutineItem) -> RoutineItem) { items[i] = f(items[i]) }
                    Panel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExerciseThumb(it.exercise, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(it.exercise, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { if (i > 0) { items.add(i - 1, items.removeAt(i)) } }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                IconButton(onClick = { if (i < items.lastIndex) { items.add(i + 1, items.removeAt(i)) } }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Default.Delete, "Remove", tint = Palette.Muted) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Stepper("Sets", "${it.targetSets}", { set { r -> r.copy(targetSets = (r.targetSets - 1).coerceAtLeast(1)) } }, { set { r -> r.copy(targetSets = r.targetSets + 1) } })
                                Stepper("Reps", "${it.targetReps}", { set { r -> r.copy(targetReps = (r.targetReps - 1).coerceAtLeast(1)) } }, { set { r -> r.copy(targetReps = r.targetReps + 1) } })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Stepper(unit, w(it.targetWeight), { set { r -> r.copy(targetWeight = (r.targetWeight - step).coerceAtLeast(0.0)) } }, { set { r -> r.copy(targetWeight = r.targetWeight + step) } })
                                Stepper(
                                    "Rest",
                                    it.restSeconds?.let { s -> "${s}s" } ?: "Default",
                                    { set { r -> r.copy(restSeconds = ((r.restSeconds ?: doc.settings.defaultRestSeconds) - 15).coerceAtLeast(15)) } },
                                    { set { r -> r.copy(restSeconds = (r.restSeconds ?: doc.settings.defaultRestSeconds) + 15) } },
                                )
                            }
                        }
                    }
                }
                item { GhostButton("Add exercise", Modifier.fillMaxWidth(), Icons.Default.Add) { picker = true } }
                item {
                    Text(
                        "Weights here are starting targets. During a workout the app suggests the next weight from your history at the selected gym.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
