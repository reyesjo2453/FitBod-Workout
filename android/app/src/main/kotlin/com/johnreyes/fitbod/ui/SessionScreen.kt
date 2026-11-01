package com.johnreyes.fitbod.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnreyes.fitbod.ActiveSession
import com.johnreyes.fitbod.Repository
import com.johnreyes.fitbod.SessionExercise
import com.johnreyes.fitbod.SessionFactory
import com.johnreyes.fitbod.SessionSet
import com.johnreyes.fitbod.core.Edits
import java.time.LocalDate

/** All edits to the in-progress workout go through here. */
private class SessionOps(private val repo: Repository) {
    private fun ex(i: Int, f: (SessionExercise) -> SessionExercise) = repo.updateSession { s ->
        s?.copy(exercises = s.exercises.mapIndexed { j, e -> if (j == i) f(e) else e })
    }

    private fun set(i: Int, k: Int, f: (SessionSet) -> SessionSet) =
        ex(i) { e -> e.copy(sets = e.sets.mapIndexed { j, s -> if (j == k) f(s) else s }) }

    fun weight(i: Int, k: Int, v: Double?) = set(i, k) { it.copy(weight = v) }
    fun reps(i: Int, k: Int, v: Int?) = set(i, k) { it.copy(reps = v) }

    fun toggle(i: Int, k: Int, now: Long) = repo.updateSession { s ->
        s ?: return@updateSession null
        val e = s.exercises[i]
        val marking = !e.sets[k].done
        val sets = e.sets.mapIndexed { j, x -> if (j == k) x.copy(done = marking) else x }
        val updated = s.copy(exercises = s.exercises.mapIndexed { j, x -> if (j == i) x.copy(sets = sets) else x })
        if (!marking) return@updateSession updated
        // Start rest and move focus to the next exercise once this one is complete.
        val next = if (sets.all { it.done }) (i + 1).coerceAtMost(s.exercises.lastIndex) else i
        updated.copy(restEndsAt = now + e.restSeconds * 1000L, restTotal = e.restSeconds, selected = next)
    }

    fun addSet(i: Int) = ex(i) { e ->
        val last = e.sets.lastOrNull()
        e.copy(sets = e.sets + SessionSet(last?.weight, last?.reps ?: e.targetReps, supersetGroup = last?.supersetGroup))
    }

    fun removeSet(i: Int, k: Int) = ex(i) { e -> e.copy(sets = e.sets.filterIndexed { j, _ -> j != k }) }

    fun select(i: Int) = repo.updateSession { it?.copy(selected = i) }

    fun addExercise(e: SessionExercise) = repo.updateSession { s ->
        s?.copy(exercises = s.exercises + e, selected = s.exercises.size)
    }

    fun replace(i: Int, e: SessionExercise) = ex(i) { e }

    fun remove(i: Int) = repo.updateSession { s ->
        s?.copy(exercises = s.exercises.filterIndexed { j, _ -> j != i }, selected = 0)
    }

    fun move(i: Int, delta: Int) = repo.updateSession { s ->
        s ?: return@updateSession null
        val j = i + delta
        if (j !in s.exercises.indices) return@updateSession s
        val list = s.exercises.toMutableList()
        list[i] = s.exercises[j]; list[j] = s.exercises[i]
        s.copy(exercises = list, selected = j)
    }

    fun rest(deltaSec: Int, now: Long) = repo.updateSession { s ->
        val end = s?.restEndsAt ?: return@updateSession s
        s.copy(restEndsAt = maxOf(now, end + deltaSec * 1000L), restTotal = (s.restTotal + deltaSec).coerceAtLeast(1))
    }

    fun skipRest() = repo.updateSession { it?.copy(restEndsAt = null) }
}

@Composable
fun SessionScreen(
    repo: Repository,
    session: ActiveSession,
    spec: LayoutSpec,
    now: Long,
    onMinimize: () -> Unit,
    onClosed: () -> Unit,
) {
    val doc by repo.doc.collectAsState()
    val ops = remember(repo) { SessionOps(repo) }
    val unit = doc.settings.units
    val context = LocalContext.current
    var picker by remember { mutableStateOf<Int?>(null) } // -1 = add, otherwise index to swap
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    // Rest timer finished: buzz and chime once.
    LaunchedEffect(now >= (session.restEndsAt ?: Long.MAX_VALUE)) {
        if (session.restEndsAt != null && now >= session.restEndsAt) {
            alert(context)
            ops.skipRest()
        }
    }

    val finish = {
        val minutes = ((System.currentTimeMillis() - session.startedAt) / 60000).toInt().coerceAtLeast(1)
        val workout = Edits.buildWorkout(
            session.syncId, LocalDate.now().toString(), session.title, session.gym, minutes,
            session.toSets(), System.currentTimeMillis(),
        )
        repo.edit { Edits.upsertWorkout(it, workout) }
        repo.updateSession { null }
        repo.syncNow()
        onClosed()
    }

    picker?.let { target ->
        ExercisePicker(
            library = doc.exercises.map { it.name },
            title = if (target < 0) "Add exercise" else "Swap exercise",
            onDismiss = { picker = null },
        ) { name ->
            val e = SessionFactory.adHoc(doc, name, session.gym)
            if (target < 0) ops.addExercise(e) else ops.replace(target, e)
            picker = null
        }
    }
    if (confirmFinish) {
        val open = session.totalSets - session.doneSets
        ConfirmDialog(
            "Finish workout?",
            if (open > 0) "$open sets aren't checked off and won't be saved." else "Nice work. This saves the workout and syncs it to Drive.",
            "Finish", onDismiss = { confirmFinish = false }, onConfirm = finish,
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            "Discard workout?", "Nothing from this session will be saved.", "Discard",
            onDismiss = { confirmDiscard = false },
        ) { repo.updateSession { null }; onClosed() }
    }

    val selected = session.selected.coerceIn(0, (session.exercises.size - 1).coerceAtLeast(0))
    val tabletop = spec.tabletop

    Column(Modifier.fillMaxSize()) {
        SessionHeader(
            session, now, onMinimize,
            onFinish = { if (session.doneSets == 0) confirmDiscard = true else confirmFinish = true },
        )
        Box(Modifier.weight(1f)) {
            when {
                session.exercises.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    EmptyState("No exercises", "Add one to get going.")
                    GhostButton("Add exercise", Modifier.align(Alignment.CenterHorizontally), Icons.Default.Add) { picker = -1 }
                }
                tabletop != null -> TabletopSession(session, selected, unit, now, ops, tabletop.hingeFraction)
                spec.wide -> TwoPane(
                    spec,
                    left = {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(session.exercises) { i, e ->
                                ExerciseListRow(e, i == selected) { ops.select(i) }
                            }
                            item { GhostButton("Add exercise", Modifier.padding(top = 8.dp), Icons.Default.Add) { picker = -1 } }
                        }
                    },
                    right = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                            ExerciseDetail(session, selected, unit, now, ops, big = true, onSwap = { picker = selected })
                        }
                    },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(session.exercises) { i, _ ->
                        Panel(
                            color = if (i == selected) Palette.Card else Palette.Surface,
                            onClick = if (i == selected) null else ({ ops.select(i) }),
                        ) {
                            ExerciseDetail(session, i, unit, now, ops, big = false, onSwap = { picker = i })
                        }
                    }
                    item { GhostButton("Add exercise", Modifier.fillMaxWidth(), Icons.Default.Add) { picker = -1 } }
                }
            }
            if (tabletop == null && session.restEndsAt != null) {
                RestBar(session, now, ops, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
        }
    }
}

@Composable
private fun SessionHeader(session: ActiveSession, now: Long, onMinimize: () -> Unit, onFinish: () -> Unit) {
    Column(Modifier.background(Palette.Bg).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize) { Icon(Icons.Default.KeyboardArrowDown, "Minimize") }
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(clock((now - session.startedAt) / 1000), session.gym).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                )
            }
            Surface(onClick = onFinish, shape = RoundedCornerShape(50), color = Palette.Accent) {
                Text("Finish", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(
            if (session.totalSets == 0) 0f else session.doneSets / session.totalSets.toFloat(),
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ExerciseListRow(e: SessionExercise, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Palette.Card else Palette.Bg)
            .then(if (selected) Modifier.border(1.dp, Palette.Accent, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumb(e.name, 44.dp, done = e.isComplete)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${e.sets.size} sets", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
        RingProgress(e.sets.count { it.done }, e.sets.size, 38.dp)
    }
}

@Composable
private fun ExerciseDetail(
    session: ActiveSession,
    index: Int,
    unit: String,
    now: Long,
    ops: SessionOps,
    big: Boolean,
    onSwap: () -> Unit,
) {
    val e = session.exercises[index]
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(e.name, if (big) 64.dp else 48.dp, done = e.isComplete)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MuscleTag(e.name)
                    Spacer(Modifier.width(8.dp))
                    Text("Rest ${e.restSeconds}s", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Exercise options", tint = Palette.Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; ops.move(index, -1) })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; ops.move(index, 1) })
                    DropdownMenuItem(text = { Text("Remove", color = Palette.Accent) }, onClick = { menu = false; ops.remove(index) })
                }
            }
        }
        if (e.reason.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                e.reason + (e.previousDate?.let { " · last $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
            )
        }
        Spacer(Modifier.height(12.dp))
        SetTable(e, index, unit, ops, now)
    }
}

@Composable
private fun SetTable(e: SessionExercise, index: Int, unit: String, ops: SessionOps, now: Long) {
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Header("SET", Modifier.width(36.dp))
            Header("PREVIOUS", Modifier.weight(1.2f))
            Header(unit.uppercase(), Modifier.weight(1f))
            Header("REPS", Modifier.weight(1f))
            Spacer(Modifier.width(52.dp))
        }
        e.sets.forEachIndexed { k, s ->
            val prev = e.previous.getOrNull(k)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(36.dp).clickable(enabled = e.sets.size > 1) { ops.removeSet(index, k) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        (s.supersetGroup ?: "") + "${k + 1}", fontWeight = FontWeight.Bold,
                        color = if (s.done) Palette.Good else Palette.Muted,
                    )
                }
                Text(
                    prev?.let { "${w(it.weight)} × ${it.reps ?: "–"}" } ?: "—",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Faint, modifier = Modifier.weight(1.2f),
                )
                DecimalField(s.weight, { ops.weight(index, k, it) }, Modifier.weight(1f).padding(end = 6.dp), s.done)
                IntField(s.reps, { ops.reps(index, k, it) }, Modifier.weight(1f).padding(start = 2.dp), s.done)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (s.done) Palette.Good else Palette.CardHi)
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            ops.toggle(index, k, now)
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, "Complete set ${k + 1}", tint = if (s.done) Palette.Bg else Palette.Muted) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Add set", icon = Icons.Default.Add) { ops.addSet(index) }
        }
        if (e.sets.size > 1) {
            Text("Tap a set number to remove it", style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = modifier, textAlign = TextAlign.Start)
}

@Composable
private fun DecimalField(value: Double?, onValue: (Double?) -> Unit, modifier: Modifier, done: Boolean) {
    var text by remember { mutableStateOf(value?.let { w(it) } ?: "") }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = value?.let { w(it) } ?: "" }
    NumberCell(text, { text = it; onValue(it.toDoubleOrNull()) }, modifier, decimal = true, highlight = done)
}

@Composable
private fun IntField(value: Int?, onValue: (Int?) -> Unit, modifier: Modifier, done: Boolean) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value?.toString() ?: "" }
    NumberCell(text, { text = it; onValue(it.toIntOrNull()) }, modifier, decimal = false, highlight = done)
}

@Composable
private fun RestBar(session: ActiveSession, now: Long, ops: SessionOps, modifier: Modifier) {
    val left = ((session.restEndsAt ?: now) - now).coerceAtLeast(0) / 1000
    Panel(modifier.fillMaxWidth(), color = Palette.CardHi, padding = PaddingValues(14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("REST", style = MaterialTheme.typography.labelSmall, color = Palette.Accent)
                    Text(clock(left), style = MaterialTheme.typography.headlineMedium)
                }
                GhostButton("−15") { ops.rest(-15, now) }
                Spacer(Modifier.width(6.dp))
                GhostButton("+15") { ops.rest(15, now) }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { ops.skipRest() }) { Icon(Icons.Default.Close, "Skip rest") }
            }
            Spacer(Modifier.height(8.dp))
            ProgressBar(if (session.restTotal == 0) 0f else left / session.restTotal.toFloat(), Modifier.fillMaxWidth())
        }
    }
}

/**
 * Flex mode: phone half-open on a bench. The top half is a glanceable "now" display
 * (current set target and rest countdown); the bottom half holds the controls.
 */
@Composable
private fun TabletopSession(session: ActiveSession, selected: Int, unit: String, now: Long, ops: SessionOps, hinge: Float) {
    val e = session.exercises[selected]
    val current = e.sets.indexOfFirst { !it.done }
    val target = e.sets.getOrNull(current) ?: e.sets.lastOrNull()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(hinge).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(e.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val resting = session.restEndsAt != null
            if (resting) {
                val left = ((session.restEndsAt ?: now) - now).coerceAtLeast(0) / 1000
                Text("REST", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                Text(clock(left), fontSize = 88.sp, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("−15") { ops.rest(-15, now) }
                    GhostButton("+15") { ops.rest(15, now) }
                    GhostButton("Skip") { ops.skipRest() }
                }
            } else if (current >= 0 && target != null) {
                Text("SET ${current + 1} OF ${e.sets.size}", style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
                Text("${w(target.weight)} $unit × ${target.reps ?: "–"}", fontSize = 56.sp, fontWeight = FontWeight.Black)
            } else {
                Text("Exercise complete", style = MaterialTheme.typography.headlineMedium, color = Palette.Good)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Column(Modifier.weight(1f - hinge).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ops.select((selected - 1).coerceAtLeast(0)) }, enabled = selected > 0) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Previous exercise")
                }
                Text(
                    "Exercise ${selected + 1} of ${session.exercises.size}", style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { ops.select((selected + 1).coerceAtMost(session.exercises.lastIndex)) }, enabled = selected < session.exercises.lastIndex) {
                    Icon(Icons.Default.KeyboardArrowRight, "Next exercise")
                }
            }
            SetTable(e, selected, unit, ops, now)
        }
    }
}

private fun alert(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
    }
}

