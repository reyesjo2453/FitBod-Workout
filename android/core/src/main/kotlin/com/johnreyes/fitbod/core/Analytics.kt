package com.johnreyes.fitbod.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Derived numbers for the Workout, Log and Progress screens. Nothing here is persisted. */
object Analytics {
    /** Epley estimate of a one-rep max. */
    fun e1rm(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

    fun doneWorkouts(doc: SyncDoc): List<Workout> =
        doc.workouts.filter { it.isDone }.sortedWith(compareBy<Workout> { it.date }.thenBy { it.updatedAt })

    // ---- per exercise --------------------------------------------------------------------

    data class Session(
        val date: String,
        val gym: String?,
        val sets: List<WSet>,
        val bestSet: WSet,
        val bestE1rm: Double,
        val volume: Double,
    )

    /** Every logged session of [exercise], oldest first. Names compare case-insensitively. */
    fun history(doc: SyncDoc, exercise: String): List<Session> = doneWorkouts(doc).mapNotNull { w ->
        val sets = w.sets.filter { it.exercise.equals(exercise, true) && it.completed && it.weight != null && it.reps != null }
        if (sets.isEmpty()) return@mapNotNull null
        val best = sets.maxBy { e1rm(it.weight!!, it.reps!!) }
        Session(
            date = w.date,
            gym = w.gym,
            sets = sets,
            bestSet = best,
            bestE1rm = e1rm(best.weight!!, best.reps!!),
            volume = sets.sumOf { it.weight!! * it.reps!! },
        )
    }

    data class Records(
        val heaviest: WSet?,
        val heaviestDate: String?,
        val bestE1rm: Double,
        val bestE1rmDate: String?,
        val bestVolume: Double,
        val sessions: Int,
    )

    fun records(doc: SyncDoc, exercise: String): Records {
        val h = history(doc, exercise)
        val heavy = h.flatMap { s -> s.sets.map { s.date to it } }
            .maxWithOrNull(compareBy<Pair<String, WSet>> { it.second.weight }.thenBy { it.second.reps })
        val e1 = h.maxByOrNull { it.bestE1rm }
        return Records(
            heaviest = heavy?.second,
            heaviestDate = heavy?.first,
            bestE1rm = e1?.bestE1rm ?: 0.0,
            bestE1rmDate = e1?.date,
            bestVolume = h.maxOfOrNull { it.volume } ?: 0.0,
            sessions = h.size,
        )
    }

    /** Exercises with logged history, most recently trained first. */
    fun trainedExercises(doc: SyncDoc): List<String> {
        val last = LinkedHashMap<String, String>()
        doneWorkouts(doc).forEach { w ->
            w.sets.filter { it.completed }.forEach { s ->
                val key = last.keys.firstOrNull { it.equals(s.exercise, true) } ?: s.exercise
                last[key] = w.date
            }
        }
        return last.entries.sortedByDescending { it.value }.map { it.key }
    }

    // ---- progression ---------------------------------------------------------------------

    data class Prescription(
        val weight: Double,
        val reps: Int,
        val sets: Int,
        /** Sets from the previous comparable session, by set index. */
        val previous: List<WSet>,
        val previousDate: String?,
        val reason: String,
    )

    /**
     * Suggests today's working weight. History from the same gym is preferred because
     * machines differ between gyms. When every set last time reached the target reps,
     * weight goes up one increment; otherwise the last weight is repeated.
     */
    fun prescribe(doc: SyncDoc, item: RoutineItem, gym: String?): Prescription {
        val all = history(doc, item.exercise)
        val sameGym = if (gym != null) all.filter { it.gym == gym } else emptyList()
        val last = sameGym.lastOrNull() ?: all.lastOrNull()
        val inc = if (doc.settings.isKg) 2.5 else 5.0
        if (last == null) {
            return Prescription(item.targetWeight, item.targetReps, item.targetSets, emptyList(), null, "Routine target")
        }
        val top = last.sets.maxOf { it.weight!! }
        val topSets = last.sets.filter { it.weight == top }
        val hitAll = last.sets.size >= item.targetSets && topSets.all { it.reps!! >= item.targetReps }
        val weight = if (hitAll) top + inc else top
        val where = if (last in sameGym && gym != null) " at $gym" else ""
        val reason = if (hitAll) "Hit ${item.targetReps}+ on every set$where — add ${fmt(inc)}"
        else "Repeat ${fmt(top)} until all sets reach ${item.targetReps}$where"
        return Prescription(weight, item.targetReps, item.targetSets, last.sets, last.date, reason)
    }

    /** The routine that follows the most recently finished one (Set A → Set B → Set A …). */
    fun nextRoutine(doc: SyncDoc): String? {
        val names = Edits.routineNames(doc)
        if (names.isEmpty()) return null
        val last = doneWorkouts(doc).lastOrNull { w -> names.any { it == w.title } } ?: return names.first()
        return names[(names.indexOf(last.title) + 1) % names.size]
    }

    // ---- totals --------------------------------------------------------------------------

    data class Totals(val workouts: Int, val sets: Int, val volume: Double, val thisWeek: Int, val streakWeeks: Int)

    fun totals(doc: SyncDoc, today: LocalDate): Totals {
        val done = doneWorkouts(doc)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = done.mapNotNull { parse(it.date) }
            .map { it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.toSet()
        var streak = 0
        var w = if (weekStart in weeks) weekStart else weekStart.minusWeeks(1)
        while (w in weeks) { streak++; w = w.minusWeeks(1) }
        return Totals(
            workouts = done.size,
            sets = done.sumOf { it.setCount ?: it.sets.size },
            volume = done.sumOf { it.volume ?: 0.0 },
            thisWeek = done.count { parse(it.date)?.let { d -> !d.isBefore(weekStart) && !d.isAfter(today) } == true },
            streakWeeks = streak,
        )
    }

    /** Volume per Monday-start week for the last [weeks] weeks, oldest first. */
    fun weeklyVolume(doc: SyncDoc, today: LocalDate, weeks: Int = 8): List<Pair<LocalDate, Double>> {
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val buckets = (weeks - 1 downTo 0).map { start.minusWeeks(it.toLong()) }
        val sums = doneWorkouts(doc).groupBy { parse(it.date)?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .mapValues { (_, ws) -> ws.sumOf { it.volume ?: 0.0 } }
        return buckets.map { it to (sums[it] ?: 0.0) }
    }

    fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    fun fmt(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else ((v * 10).roundToInt() / 10.0).toString()
}
