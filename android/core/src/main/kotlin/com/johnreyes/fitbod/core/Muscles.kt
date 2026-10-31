package com.johnreyes.fitbod.core

/**
 * Display-only guess of the primary muscle for an exercise name. The sync file does
 * not store muscle targets, so nothing here is written back.
 */
object Muscles {
    private val rules = listOf(
        listOf("calf") to "Calves",
        listOf("leg curl", "romanian", "rdl", "hamstring") to "Hamstrings",
        listOf("hip thrust", "glute") to "Glutes",
        listOf("leg press", "squat", "leg extension", "lunge", "quad") to "Quads",
        listOf("deadlift") to "Back",
        listOf("crunch", "plank", "ab ", "abs", "core") to "Abs",
        listOf("tricep", "pushdown", "dip", "skull") to "Triceps",
        listOf("bicep", "curl") to "Biceps",
        listOf("shoulder", "overhead", "lateral", "delt", "military") to "Shoulders",
        listOf("row", "pulldown", "pull up", "pull-up", "chin", "lat ") to "Back",
        listOf("chest", "bench", "fly", "pec", "push up", "push-up") to "Chest",
    )

    fun primary(exercise: String): String {
        val n = exercise.lowercase() + " "
        return rules.firstOrNull { (keys, _) -> keys.any { n.contains(it) } }?.second ?: "Full Body"
    }
}
