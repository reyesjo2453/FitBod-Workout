package com.johnreyes.fitbod.core

import java.util.UUID

/** Pure document edits. Each returns the new document and the sections it touched. */
data class Edit(val doc: SyncDoc, val dirty: Set<Section> = emptySet())

object Edits {
    // ---- routines ------------------------------------------------------------------------

    fun routineNames(doc: SyncDoc): List<String> = doc.routines.map { it.routine }.distinct().sorted()

    fun routineItems(doc: SyncDoc, routine: String): List<RoutineItem> =
        doc.routines.filter { it.routine == routine }.sortedBy { it.sortOrder }

    /** Replaces every item of [routine] with [items] (renumbering sortOrder 1..n). */
    fun saveRoutine(doc: SyncDoc, routine: String, items: List<RoutineItem>, newName: String = routine): Edit {
        val renumbered = items.mapIndexed { i, it -> it.copy(routine = newName, sortOrder = i + 1) }
        val others = doc.routines.filter { it.routine != routine && it.routine != newName }
        val withExercises = ensureExercises(doc, renumbered.map { it.exercise })
        return Edit(
            withExercises.doc.copy(routines = others + renumbered),
            withExercises.dirty + Section.ROUTINES,
        )
    }

    fun deleteRoutine(doc: SyncDoc, routine: String): Edit =
        Edit(doc.copy(routines = doc.routines.filter { it.routine != routine }), setOf(Section.ROUTINES))

    // ---- exercises -----------------------------------------------------------------------

    fun ensureExercises(doc: SyncDoc, names: Collection<String>): Edit {
        val known = doc.exercises.map { it.name.lowercase() }.toMutableSet()
        var nextId = (doc.exercises.maxOfOrNull { it.id } ?: 0) + 1
        val added = names.filter { it.isNotBlank() && known.add(it.lowercase()) }
            .map { Exercise(id = nextId++, name = it, baseline = "no PR yet", cue = DEFAULT_CUE) }
        if (added.isEmpty()) return Edit(doc)
        return Edit(doc.copy(exercises = doc.exercises + added), setOf(Section.EXERCISES))
    }

    fun upsertExercise(doc: SyncDoc, exercise: Exercise): Edit {
        val exists = doc.exercises.any { it.id == exercise.id }
        val list = if (exists) doc.exercises.map { if (it.id == exercise.id) exercise else it }
        else doc.exercises + exercise.copy(id = (doc.exercises.maxOfOrNull { it.id } ?: 0) + 1)
        return Edit(doc.copy(exercises = list), setOf(Section.EXERCISES))
    }

    // ---- workouts ------------------------------------------------------------------------

    fun newSyncId(): String = "local-" + UUID.randomUUID()

    /**
     * Builds a finished workout record. Only completed sets are stored, numbered
     * 1..n per exercise, and the summary fields are derived from them.
     */
    fun buildWorkout(
        syncId: String,
        date: String,
        title: String,
        gym: String?,
        durationMinutes: Int?,
        sets: List<WSet>,
        now: Long,
    ): Workout {
        val done = sets.filter { it.completed }
        val counters = HashMap<String, Int>()
        val numbered = done.map { s ->
            val n = (counters[s.exercise] ?: 0) + 1
            counters[s.exercise] = n
            s.copy(setNumber = n)
        }
        return Workout(
            syncId = syncId,
            updatedAt = now,
            date = date,
            title = title,
            volume = numbered.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) },
            setCount = numbered.size,
            durationMinutes = durationMinutes,
            gym = gym,
            exerciseNames = numbered.map { it.exercise }.distinct(),
            status = Workout.STATUS_DONE,
            sets = numbered,
        )
    }

    fun upsertWorkout(doc: SyncDoc, workout: Workout): Edit {
        val ensured = ensureExercises(doc, workout.exerciseNames)
        val d = ensured.doc
        val list = if (d.workouts.any { it.syncId == workout.syncId }) {
            d.workouts.map { if (it.syncId == workout.syncId) workout else it }
        } else d.workouts + workout
        return Edit(
            d.copy(workouts = list.sortedWith(compareBy<Workout> { it.date }.thenBy { it.updatedAt })),
            ensured.dirty,
        )
    }

    fun deleteWorkout(doc: SyncDoc, syncId: String, now: Long): Edit = Edit(
        doc.copy(
            workouts = doc.workouts.filter { it.syncId != syncId },
            tombstones = doc.tombstones.filter { it.syncId != syncId } + Tombstone(syncId, now),
        ),
    )

    // ---- gyms & settings -----------------------------------------------------------------

    fun setGyms(doc: SyncDoc, gyms: List<String>): Edit =
        Edit(doc.copy(gyms = gyms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()), setOf(Section.GYMS))

    fun setSetting(doc: SyncDoc, key: String, value: String): Edit =
        Edit(doc.copy(settings = doc.settings.with(key, value)), setOf(Section.SETTINGS))

    const val DEFAULT_CUE = "Control the eccentric and keep the setup consistent."
}
