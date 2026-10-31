package com.johnreyes.fitbod.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeAndAnalyticsTest {
    private val base = SyncCodec.decode(javaClass.getResource("/sample-sync.json")!!.readText())

    private fun workout(id: String, updated: Long, date: String = "2026-10-30", title: String = "Set B — Full Body") =
        Workout(syncId = id, updatedAt = updated, date = date, title = title)

    @Test
    fun newerWorkoutWinsAndBothSidesAreKept() {
        val local = base.copy(workouts = base.workouts + workout("a", 10) + workout("local-only", 5))
        val remote = base.copy(workouts = base.workouts + workout("a", 20).copy(gym = "Home") + workout("merlin-1", 7))
        val merged = Merge.merge(local, remote, emptySet(), now = 99)
        val ids = merged.workouts.map { it.syncId }
        assertTrue(listOf("a", "local-only", "merlin-1").all { it in ids })
        assertEquals("Home", merged.workouts.first { it.syncId == "a" }.gym)
        assertEquals(99, merged.exportedAt)
    }

    @Test
    fun tombstonesDeleteOnBothSides() {
        val deleted = Edits.deleteWorkout(base, "pc-workout-1", now = 50).doc
        val merged = Merge.merge(deleted, base, emptySet(), now = 60)
        assertFalse(merged.workouts.any { it.syncId == "pc-workout-1" })
        assertEquals(listOf("pc-workout-1"), merged.tombstones.map { it.syncId })
    }

    @Test
    fun dirtySectionsPreferLocal() {
        val edit = Edits.saveRoutine(base, "Set B — Full Body", Edits.routineItems(base, "Set B — Full Body"), "Set B — Upper")
        val remote = Edits.setSetting(base, Settings.KEY_UNITS, "kg").doc
        val merged = Merge.merge(edit.doc, remote, edit.dirty, now = 1)
        assertTrue("Set B — Upper" in Edits.routineNames(merged))
        assertEquals("kg", merged.settings.units)
    }

    @Test
    fun exercisesAreUnioned() {
        val remote = Edits.ensureExercises(base, listOf("Seated Calf Raise")).doc
        val local = Edits.ensureExercises(base, listOf("Pull Up")).doc
        val names = Merge.merge(local, remote, emptySet(), 1).exercises.map { it.name }
        assertTrue("Seated Calf Raise" in names && "Pull Up" in names)
        assertEquals(names.size, Merge.merge(local, remote, emptySet(), 1).exercises.map { it.id }.toSet().size)
    }

    @Test
    fun buildWorkoutMatchesOriginalShape() {
        val sets = listOf(
            WSet("Machine Chest Press", 1, 100.0, 10),
            WSet("Machine Chest Press", 2, 100.0, 8, completed = false),
            WSet("Machine Chest Press", 3, 110.0, 8),
        )
        val w = Edits.buildWorkout("local-x", "2026-10-31", "Set A — Full Body", "Base Gym", 40, sets, now = 5)
        assertEquals(2, w.setCount)
        assertEquals(1880.0, w.volume)
        assertEquals(listOf(1, 2), w.sets.map { it.setNumber })
        assertEquals(listOf("Machine Chest Press"), w.exerciseNames)
    }

    @Test
    fun prescriptionProgressesWhenAllSetsHitTarget() {
        val item = Edits.routineItems(base, "Set A — Full Body").first()
        val withHit = Edits.upsertWorkout(
            base,
            Edits.buildWorkout(
                "w2", "2026-10-27", item.routine, "Base Gym", null,
                List(3) { WSet(item.exercise, it + 1, 110.0, 10) }, now = 2,
            ),
        ).doc
        val p = Analytics.prescribe(withHit, item, "Base Gym")
        assertEquals(115.0, p.weight)
        // Sample history at Base Gym missed reps on the last set, so it repeats.
        assertEquals(100.5, Analytics.prescribe(base, item, "Base Gym").weight)
    }

    @Test
    fun rotationAlternatesRoutines() {
        assertEquals("Set B — Full Body", Analytics.nextRoutine(base))
    }

    @Test
    fun totalsAndWeeklyVolume() {
        val t = Analytics.totals(base, LocalDate.parse("2026-10-24"))
        assertEquals(2, t.workouts)
        assertEquals(1, t.thisWeek)
        val weeks = Analytics.weeklyVolume(base, LocalDate.parse("2026-10-24"), 6)
        assertEquals(6, weeks.size)
        assertEquals(2700.0, weeks.last().second)
    }

    @Test
    fun e1rmAndRecords() {
        assertEquals(133.33, Analytics.e1rm(100.0, 10), 0.01)
        val r = Analytics.records(base, "machine chest press")
        assertEquals(100.5, r.heaviest?.weight)
        assertEquals(1, r.sessions)
    }
}
