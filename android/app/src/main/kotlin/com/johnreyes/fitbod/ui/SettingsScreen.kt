package com.johnreyes.fitbod.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.SyncState
import com.johnreyes.fitbod.core.Edits
import com.johnreyes.fitbod.core.Exercise
import com.johnreyes.fitbod.core.Settings
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    repo: Repository,
    spec: LayoutSpec,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onEditRoutine: (String) -> Unit,
) {
    val doc by repo.doc.collectAsState()
    val account by repo.account.collectAsState()
    val sync by repo.sync.collectAsState()
    val unsynced by repo.hasUnsynced.collectAsState()
    val context = LocalContext.current
    var prompt by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var editExercise by remember { mutableStateOf<Exercise?>(null) }
    var confirmImport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(repo.export().toByteArray()) } }
            .onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        confirmImport = runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }.getOrNull()
    }

    prompt?.let { (title, done) -> TextPrompt(title, "", "Name", onDismiss = { prompt = null }, onDone = done) }
    confirmImport?.let { text ->
        ConfirmDialog(
            "Import backup?",
            "Routines, gyms and settings are replaced by the backup. Workouts from both are kept.",
            "Import", onDismiss = { confirmImport = null },
        ) {
            runCatching { repo.import(text) }
                .onSuccess { repo.syncNow() }
                .onFailure { Toast.makeText(context, "That file isn't a FitBod backup", Toast.LENGTH_LONG).show() }
        }
    }
    editExercise?.let { ex -> ExerciseEditor(ex, onDismiss = { editExercise = null }) { repo.edit { d -> Edits.upsertExercise(d, it) } } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = if (spec.wide) 760.dp else 10_000.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ScreenTitle("Settings") }

            item { SectionLabel("Google Drive sync") }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        if (account == null) {
                            Text("Not connected", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect to sync with the FitBod folder in your Drive (fitbod-sync.json). The app can only see files it created there.",
                                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton("Connect Google Drive", Modifier.fillMaxWidth(), onClick = onSignIn)
                        } else {
                            Text(account!!, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (val s = sync) {
                                    SyncState.Idle -> "Not synced yet"
                                    SyncState.Syncing -> "Syncing…"
                                    is SyncState.Done -> "Synced " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.at)) +
                                        if (unsynced) " · local changes pending" else ""
                                    is SyncState.Failed -> "Sync failed: ${s.message}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sync is SyncState.Failed) Palette.Accent else Palette.Muted,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GhostButton("Sync now", icon = Icons.Default.Refresh) { repo.syncNow() }
                                GhostButton("Disconnect", color = Palette.Muted, onClick = onSignOut)
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Training") }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Units", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            listOf("lbs", "kg").forEach { u ->
                                Chip(u, doc.settings.units.equals(u, true), Modifier.padding(start = 6.dp)) {
                                    repo.edit { Edits.setSetting(it, Settings.KEY_UNITS, u) }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Default rest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Stepper(
                                "seconds", "${doc.settings.defaultRestSeconds}",
                                { repo.edit { Edits.setSetting(it, Settings.KEY_REST, (doc.settings.defaultRestSeconds - 15).coerceAtLeast(15).toString()) } },
                                { repo.edit { Edits.setSetting(it, Settings.KEY_REST, (doc.settings.defaultRestSeconds + 15).toString()) } },
                            )
                        }
                        SettingRow("Goal", doc.settings.gymGoal) {
                            prompt = "Training goal" to { v: String -> repo.edit { Edits.setSetting(it, Settings.KEY_GOAL, v) } }
                        }
                        SettingRow("Split", doc.settings.split) {
                            prompt = "Split" to { v: String -> repo.edit { Edits.setSetting(it, Settings.KEY_SPLIT, v) } }
                        }
                    }
                }
            }

            item { SectionLabel("Workouts") }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        Edits.routineNames(doc).forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r, style = MaterialTheme.typography.titleSmall)
                                    Text("${Edits.routineItems(doc, r).size} exercises", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                                IconButton(onClick = { onEditRoutine(r) }) { Icon(Icons.Default.Edit, "Edit $r", tint = Palette.Muted) }
                            }
                        }
                        GhostButton("New workout", Modifier.padding(top = 8.dp), Icons.Default.Add) {
                            prompt = "New workout" to { v: String -> onEditRoutine(v) }
                        }
                    }
                }
            }

            item { SectionLabel("Gyms") }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        doc.gyms.forEach { g ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(g, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { repo.edit { Edits.setGyms(it, it.gyms - g) } }) {
                                    Icon(Icons.Default.Close, "Remove $g", tint = Palette.Muted)
                                }
                            }
                        }
                        GhostButton("Add gym", Modifier.padding(top = 8.dp), Icons.Default.Add) {
                            prompt = "Add gym" to { v: String -> repo.edit { Edits.setGyms(it, it.gyms + v) } }
                        }
                    }
                }
            }

            item { SectionLabel("Exercise library · ${doc.exercises.size}") }
            item {
                Panel(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)) {
                    Column {
                        doc.exercises.sortedBy { it.name.lowercase() }.forEach { ex ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExerciseThumb(ex.name, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, style = MaterialTheme.typography.titleSmall)
                                    ex.baseline?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Muted) }
                                }
                                IconButton(onClick = { editExercise = ex }) { Icon(Icons.Default.Edit, "Edit ${ex.name}", tint = Palette.Muted) }
                            }
                        }
                        GhostButton("New exercise", Modifier.padding(vertical = 8.dp), Icons.Default.Add) {
                            editExercise = Exercise(id = -1, name = "", baseline = "no PR yet", cue = Edits.DEFAULT_CUE)
                        }
                    }
                }
            }

            item { SectionLabel("Backup") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Export JSON") { exportLauncher.launch("fitbod-sync.json") }
                    GhostButton("Import JSON") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
            }
            item {
                Text(
                    "Backups use the same format as fitbod-sync.json in Drive.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Faint, modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        IconButton(onClick = onClick) { Icon(Icons.Default.Edit, "Change $label", tint = Palette.Muted) }
    }
}

@Composable
private fun ExerciseEditor(ex: Exercise, onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    var name by remember { mutableStateOf(ex.name) }
    var baseline by remember { mutableStateOf(ex.baseline.orEmpty()) }
    var cue by remember { mutableStateOf(ex.cue.orEmpty()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(if (ex.id < 0) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    name, { name = it }, label = { Text("Name") }, singleLine = true,
                    // Renaming would orphan history and routines that refer to the old name.
                    enabled = ex.id < 0,
                )
                androidx.compose.material3.OutlinedTextField(baseline, { baseline = it }, label = { Text("Baseline") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(cue, { cue = it }, label = { Text("Coaching cue") })
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(ex.copy(name = name.trim(), baseline = baseline.ifBlank { null }, cue = cue.ifBlank { null })); onDismiss() },
            ) { Text("Save", color = Palette.Accent) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
