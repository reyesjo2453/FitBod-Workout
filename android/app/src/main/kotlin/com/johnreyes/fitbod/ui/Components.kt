package com.johnreyes.fitbod.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnreyes.fitbod.core.Analytics
import com.johnreyes.fitbod.core.Muscles

// ---- formatting ----------------------------------------------------------------------------

fun w(v: Double?): String = v?.let { Analytics.fmt(it) } ?: "–"

fun bigNumber(v: Double): String = when {
    v >= 1_000_000 -> Analytics.fmt(v / 1_000_000.0) + "M"
    v >= 10_000 -> Analytics.fmt(Math.round(v / 100.0) / 10.0) + "k"
    else -> Analytics.fmt(Math.round(v).toDouble())
}

fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ---- building blocks -----------------------------------------------------------------------

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Palette.Card,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Accent)
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Palette.Accent, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50),
        color = if (enabled) color else Palette.CardHi, contentColor = Color.White,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 17.sp)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Palette.Text, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = Palette.CardHi, contentColor = color, modifier = modifier) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) Palette.Text else Palette.Card,
        contentColor = if (selected) Palette.Bg else Palette.Text,
        modifier = modifier,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleSmall, maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** FitBod-style square thumbnail: muscle-coloured tile with the exercise's initials. */
@Composable
fun ExerciseThumb(name: String, size: Dp = 52.dp, done: Boolean = false) {
    val muscle = Muscles.primary(name)
    val tint = Palette.muscle(muscle)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(if (done) Palette.GoodDim else tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Default.Check, null, tint = Palette.Good, modifier = Modifier.size(size / 2))
        } else {
            val initials = name.split(' ', '/', '(').filter { it.isNotBlank() && it[0].isLetter() }.take(2).joinToString("") { it.take(1).uppercase() }
            Text(initials, color = tint, fontWeight = FontWeight.Black, fontSize = (size.value * 0.34f).sp)
        }
    }
}

@Composable
fun MuscleTag(name: String) {
    val muscle = Muscles.primary(name)
    Text(
        muscle, color = Palette.muscle(muscle), style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.muscle(muscle).copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Palette.Text) {
    Panel(modifier, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1)
        }
    }
}

/** A compact numeric entry cell used in set rows. */
@Composable
fun NumberCell(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    highlight: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = { s -> if (s.length <= 6 && s.all { it.isDigit() || (decimal && it == '.') }) onValue(s) },
        singleLine = true,
        textStyle = TextStyle(color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Palette.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) Palette.GoodDim else Palette.CardHi)
            .padding(vertical = 10.dp),
    )
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("−", onMinus)
            Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp))
            RoundIcon("+", onPlus)
        }
    }
}

@Composable
private fun RoundIcon(symbol: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(Palette.CardHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Accent) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.CardHi)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

@Composable
fun RingProgress(done: Int, total: Int, size: Dp = 40.dp) {
    val frac = if (total == 0) 0f else done / total.toFloat()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Palette.CardHi, 0f, 360f, false, style = stroke)
            drawArc(if (frac >= 1f) Palette.Good else Palette.Accent, -90f, 360f * frac, false, style = stroke)
        }
        Text("$done/$total", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.AccentDim), contentAlignment = Alignment.Center) {
            Icon(Glyphs.Dumbbell, null, tint = Palette.Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}

// ---- exercise picker -----------------------------------------------------------------------

@Composable
fun ExercisePicker(library: List<String>, title: String = "Add exercise", onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val matches = library.filter { it.contains(query.trim(), ignoreCase = true) }.sorted()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search or type a new exercise") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val q = query.trim()
                    if (q.isNotEmpty() && library.none { it.equals(q, ignoreCase = true) }) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(q) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Add, null, tint = Palette.Accent)
                                Spacer(Modifier.width(12.dp))
                                Text("Create \"$q\"", color = Palette.Accent, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    items(matches) { name ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(name) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseThumb(name, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(Muscles.primary(name), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = { Text(body, color = Palette.Muted) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = Palette.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextPrompt(title: String, initial: String, label: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()); onDismiss() }) { Text("Save", color = Palette.Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
}

@Composable
fun Outline(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, Palette.Line, RoundedCornerShape(16.dp)).padding(12.dp)) { content() }
}

// ---- custom glyphs -------------------------------------------------------------------------

object Glyphs {
    val Dumbbell: ImageVector by lazy {
        ImageVector.Builder("Dumbbell", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(2f, 9f); horizontalLineTo(4f); verticalLineTo(15f); horizontalLineTo(2f); close()
                moveTo(5f, 6f); horizontalLineTo(8f); verticalLineTo(18f); horizontalLineTo(5f); close()
                moveTo(9f, 11f); horizontalLineTo(15f); verticalLineTo(13f); horizontalLineTo(9f); close()
                moveTo(16f, 6f); horizontalLineTo(19f); verticalLineTo(18f); horizontalLineTo(16f); close()
                moveTo(20f, 9f); horizontalLineTo(22f); verticalLineTo(15f); horizontalLineTo(20f); close()
            }
        }.build()
    }

    val Bars: ImageVector by lazy {
        ImageVector.Builder("Bars", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 13f); horizontalLineTo(8f); verticalLineTo(20f); horizontalLineTo(4f); close()
                moveTo(10f, 8f); horizontalLineTo(14f); verticalLineTo(20f); horizontalLineTo(10f); close()
                moveTo(16f, 4f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(16f); close()
            }
        }.build()
    }

    val Calendar: ImageVector by lazy {
        ImageVector.Builder("Calendar", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(9f); horizontalLineTo(4f); close()
                moveTo(4f, 10f); horizontalLineTo(20f); verticalLineTo(20f); horizontalLineTo(4f); close()
                moveTo(7f, 2f); horizontalLineTo(9f); verticalLineTo(6f); horizontalLineTo(7f); close()
                moveTo(15f, 2f); horizontalLineTo(17f); verticalLineTo(6f); horizontalLineTo(15f); close()
            }
        }.build()
    }
}
