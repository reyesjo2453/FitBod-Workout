package com.johnreyes.fitbod.core

import kotlinx.serialization.json.JsonObject

/**
 * Combines this device's document with the copy in Google Drive.
 *
 * - Workouts are matched by `syncId`; the newer `updatedAt` wins. On a tie the Drive
 *   copy wins, so an edit another tool made to the file without bumping `updatedAt`
 *   isn't overwritten (the phone always bumps `updatedAt` when it edits a workout).
 * - Tombstones are unioned and remove any workout not edited after its deletion.
 * - Exercises are unioned by name, so history never points at a missing exercise.
 * - Routines, templates, recovery, gyms and settings have no per-record timestamps:
 *   the local copy wins for sections edited here since the last sync, otherwise
 *   the Drive copy wins.
 */
object Merge {
    fun merge(local: SyncDoc, remote: SyncDoc, localDirty: Set<Section>, now: Long): SyncDoc {
        val tombstones = (remote.tombstones + local.tombstones)
            .groupBy { it.syncId }
            .map { (_, ts) -> ts.maxBy { it.deletedAt } }
        val deletedAt = tombstones.associate { it.syncId to it.deletedAt }

        val byId = LinkedHashMap<String, Workout>()
        for (w in remote.workouts) byId[w.syncId] = w
        for (w in local.workouts) {
            val other = byId[w.syncId]
            if (other == null || w.updatedAt > other.updatedAt) byId[w.syncId] = w
        }
        val workouts = byId.values
            .filter { w -> deletedAt[w.syncId]?.let { w.updatedAt > it } ?: true }
            .sortedWith(compareBy<Workout> { it.date }.thenBy { it.updatedAt })

        fun <T> pick(section: Section, l: T, r: T): T = if (section in localDirty) l else r

        val exercises = if (Section.EXERCISES in localDirty) {
            unionExercises(local.exercises, remote.exercises)
        } else {
            unionExercises(remote.exercises, local.exercises)
        }

        return SyncDoc(
            version = maxOf(SyncDoc.FORMAT_VERSION, remote.version, local.version),
            exportedAt = now,
            exercises = exercises,
            routines = pick(Section.ROUTINES, local.routines, remote.routines),
            workouts = workouts,
            templates = pick(Section.TEMPLATES, local.templates, remote.templates),
            recovery = pick(Section.RECOVERY, local.recovery, remote.recovery),
            gyms = pick(Section.GYMS, local.gyms, remote.gyms),
            settings = pick(Section.SETTINGS, local.settings, remote.settings),
            tombstones = tombstones,
            extra = JsonObject(local.extra + remote.extra),
        )
    }

    /** [primary] wins on name clashes; names only in [secondary] are appended with free ids. */
    private fun unionExercises(primary: List<Exercise>, secondary: List<Exercise>): List<Exercise> {
        val names = primary.map { it.name.lowercase() }.toMutableSet()
        val ids = primary.map { it.id }.toMutableSet()
        var nextId = (primary + secondary).maxOfOrNull { it.id }?.plus(1) ?: 1
        val out = primary.toMutableList()
        for (e in secondary) {
            if (!names.add(e.name.lowercase())) continue
            val id = if (ids.add(e.id)) e.id else nextId++.also { ids.add(it) }
            out += e.copy(id = id)
        }
        return out
    }
}
