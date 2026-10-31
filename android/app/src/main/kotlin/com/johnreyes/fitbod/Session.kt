package com.johnreyes.fitbod

import com.johnreyes.fitbod.core.Analytics
import com.johnreyes.fitbod.core.Edits
import com.johnreyes.fitbod.core.RoutineItem
import com.johnreyes.fitbod.core.SyncDoc
import com.johnreyes.fitbod.core.WSet
import kotlinx.serialization.Serializable

/**
 * A workout in progress. It lives only on this device (saved in `session.json`) and
 * becomes a normal `done` workout in the sync file when finished.
 */
@Serializable
data class ActiveSession(
    val syncId: String,
    val title: String,
    val gym: String?,
    val startedAt: Long,
    val exercises: List<SessionExercise>,
    val selected: Int = 0,
    val restEndsAt: Long? = null,
    val restTotal: Int = 0,
) {
    val doneSets: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }
    val totalSets: Int get() = exercises.sumOf { it.sets.size }
    val volume: Double get() = exercises.sumOf { e -> e.sets.filter { it.done }.sumOf { (it.weight ?: 0.0) * (it.reps ?: 0) } }

    fun toSets(): List<WSet> = exercises.flatMap { e ->
        e.sets.mapIndexed { i, s -> WSet(e.name, i + 1, s.weight, s.reps, s.done, s.supersetGroup) }
    }
}

@Serializable
data class SessionExercise(
    val name: String,
    val restSeconds: Int,
    val targetReps: Int,
    val sets: List<SessionSet>,
    val previous: List<PrevSet> = emptyList(),
    val previousDate: String? = null,
    val reason: String = "",
) {
    val isComplete: Boolean get() = sets.isNotEmpty() && sets.all { it.done }
}

@Serializable
data class SessionSet(
    val weight: Double?,
    val reps: Int?,
    val done: Boolean = false,
    val supersetGroup: String? = null,
)

@Serializable
data class PrevSet(val weight: Double?, val reps: Int?)

object SessionFactory {
    fun fromRoutine(doc: SyncDoc, routine: String, gym: String?, now: Long): ActiveSession = ActiveSession(
        syncId = Edits.newSyncId(),
        title = routine,
        gym = gym,
        startedAt = now,
        exercises = Edits.routineItems(doc, routine).map { exercise(doc, it, gym) },
    )

    fun exercise(doc: SyncDoc, item: RoutineItem, gym: String?): SessionExercise {
        val p = Analytics.prescribe(doc, item, gym)
        return SessionExercise(
            name = item.exercise,
            restSeconds = item.restSeconds ?: doc.settings.defaultRestSeconds,
            targetReps = item.targetReps,
            sets = List(p.sets.coerceAtLeast(1)) { SessionSet(p.weight, p.reps) },
            previous = p.previous.map { PrevSet(it.weight, it.reps) },
            previousDate = p.previousDate,
            reason = p.reason,
        )
    }

    /** An exercise added mid-workout, prefilled from its own history when there is some. */
    fun adHoc(doc: SyncDoc, name: String, gym: String?): SessionExercise {
        val last = Analytics.history(doc, name).lastOrNull()
        val item = RoutineItem(
            routine = "", exercise = name, targetSets = 3,
            targetWeight = last?.bestSet?.weight ?: 0.0, targetReps = 10,
            restSeconds = null, sortOrder = 0,
        )
        return exercise(doc, item, gym)
    }
}
