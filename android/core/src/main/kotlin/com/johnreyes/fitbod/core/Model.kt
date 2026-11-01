package com.johnreyes.fitbod.core

import kotlinx.serialization.json.JsonObject

/**
 * In-memory form of `fitbod-sync.json` (format version 2).
 *
 * Every record keeps an [extra] object holding any keys this app does not know
 * about, and [SyncDoc.templates] is kept verbatim. Writing a document back out
 * therefore reproduces everything the original app (or another writer of the
 * file) put there, so older and newer clients can share the same file.
 */
data class SyncDoc(
    val version: Int = FORMAT_VERSION,
    val exportedAt: Long = 0,
    val exercises: List<Exercise> = emptyList(),
    val routines: List<RoutineItem> = emptyList(),
    val workouts: List<Workout> = emptyList(),
    val templates: List<JsonObject> = emptyList(),
    val recovery: List<RecoveryRow> = emptyList(),
    val gyms: List<String> = emptyList(),
    val settings: Settings = Settings(),
    val tombstones: List<Tombstone> = emptyList(),
    val extra: JsonObject = EMPTY,
) {
    companion object {
        const val FORMAT_VERSION = 2
    }
}

data class Exercise(
    val id: Long,
    val name: String,
    val baseline: String? = null,
    val cue: String? = null,
    val extra: JsonObject = EMPTY,
)

/** One exercise slot in a routine such as "Set A — Full Body". */
data class RoutineItem(
    val routine: String,
    val exercise: String,
    val targetSets: Int,
    val targetWeight: Double,
    val targetReps: Int,
    val restSeconds: Int? = null,
    val sortOrder: Int,
    val extra: JsonObject = EMPTY,
)

data class Workout(
    val syncId: String,
    val updatedAt: Long,
    val date: String,
    val title: String,
    val volume: Double? = null,
    val setCount: Int? = null,
    val durationMinutes: Int? = null,
    val gym: String? = null,
    val exerciseNames: List<String> = emptyList(),
    val status: String = STATUS_DONE,
    val sets: List<WSet> = emptyList(),
    val extra: JsonObject = EMPTY,
) {
    val isDone: Boolean get() = status == STATUS_DONE

    /** Exercise names in logged order, falling back to sets when the list is empty. */
    val exercisesInOrder: List<String>
        get() = exerciseNames.ifEmpty { sets.map { it.exercise }.distinct() }

    companion object {
        const val STATUS_DONE = "done"
    }
}

data class WSet(
    val exercise: String,
    val setNumber: Int,
    val weight: Double?,
    val reps: Int?,
    val completed: Boolean = true,
    val supersetGroup: String? = null,
    val extra: JsonObject = EMPTY,
)

data class RecoveryRow(
    val muscle: String,
    val availableAt: Long? = null,
    val extra: JsonObject = EMPTY,
)

data class Tombstone(
    val syncId: String,
    val deletedAt: Long,
    val extra: JsonObject = EMPTY,
)

/**
 * Settings are stored as a flat object of string values, e.g.
 * `"default_rest_seconds": "90"`. The raw object is kept so unknown keys survive.
 */
data class Settings(val raw: JsonObject = EMPTY) {
    fun string(key: String): String? = raw.stringOrNull(key)

    val gymGoal: String get() = string(KEY_GOAL) ?: "Hypertrophy 8–12"
    val split: String get() = string(KEY_SPLIT) ?: "Set A / Set B rotation"
    val units: String get() = string(KEY_UNITS) ?: "lbs"
    val defaultRestSeconds: Int get() = string(KEY_REST)?.trim()?.toDoubleOrNull()?.toInt() ?: 90
    val isKg: Boolean get() = units.equals("kg", ignoreCase = true)

    fun with(key: String, value: String): Settings =
        Settings(JsonObject(raw + (key to kotlinx.serialization.json.JsonPrimitive(value))))

    companion object {
        const val KEY_GOAL = "gym_goal"
        const val KEY_SPLIT = "split"
        const val KEY_UNITS = "units"
        const val KEY_REST = "default_rest_seconds"
    }
}

/** Sections of the document that have no per-record timestamps. */
enum class Section { EXERCISES, ROUTINES, TEMPLATES, RECOVERY, GYMS, SETTINGS }

val EMPTY = JsonObject(emptyMap())
