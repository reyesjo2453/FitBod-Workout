package com.johnreyes.fitbod.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull

/**
 * Reads and writes the `fitbod-sync.json` format. Key names and order match the
 * file the original Android app writes; unknown keys are carried through.
 */
object SyncCodec {
    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val writer = Json { prettyPrint = true; prettyPrintIndent = " " }

    fun decode(text: String): SyncDoc = decode(reader.parseToJsonElement(text) as JsonObject)

    fun encode(doc: SyncDoc): String = writer.encodeToString(JsonObject.serializer(), toJson(doc))

    fun decode(o: JsonObject): SyncDoc = SyncDoc(
        version = o.int("version") ?: SyncDoc.FORMAT_VERSION,
        exportedAt = o.long("exportedAt") ?: 0,
        exercises = o.objects("exercises").mapIndexed { i, e -> exercise(e, i + 1L) },
        routines = o.objects("routines").mapIndexed { i, r -> routine(r, i + 1) },
        workouts = o.objects("workouts").mapIndexed { i, w -> workout(w, i + 1) },
        templates = o.objects("templates"),
        recovery = o.objects("recovery").map(::recovery),
        gyms = o.array("gyms").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        settings = Settings((o["settings"] as? JsonObject) ?: EMPTY),
        tombstones = o.objects("tombstones").mapNotNull(::tombstone),
        extra = o.without(DOC_KEYS),
    )

    fun toJson(d: SyncDoc): JsonObject = obj(
        d.extra,
        "version" to num(d.version),
        "exportedAt" to num(d.exportedAt),
        "exercises" to JsonArray(d.exercises.map(::toJson)),
        "routines" to JsonArray(d.routines.map(::toJson)),
        "workouts" to JsonArray(d.workouts.map(::toJson)),
        "templates" to JsonArray(d.templates),
        "recovery" to JsonArray(d.recovery.map(::toJson)),
        "gyms" to JsonArray(d.gyms.map(::JsonPrimitive)),
        "settings" to d.settings.raw,
        "tombstones" to JsonArray(d.tombstones.map(::toJson)),
    )

    // ---- records -------------------------------------------------------------------------

    private fun exercise(o: JsonObject, fallbackId: Long) = Exercise(
        id = o.long("id") ?: fallbackId,
        name = o.string("name").orEmpty(),
        baseline = o.string("baseline"),
        cue = o.string("cue"),
        extra = o.without(EXERCISE_KEYS),
    )

    fun toJson(e: Exercise) = obj(
        e.extra,
        "id" to num(e.id), "name" to str(e.name), "baseline" to str(e.baseline), "cue" to str(e.cue),
    )

    private fun routine(o: JsonObject, fallbackOrder: Int) = RoutineItem(
        routine = o.string("routine").orEmpty(),
        exercise = o.string("exercise").orEmpty(),
        targetSets = o.int("targetSets") ?: 3,
        targetWeight = o.double("targetWeight") ?: 0.0,
        targetReps = o.int("targetReps") ?: 10,
        restSeconds = o.int("restSeconds"),
        sortOrder = o.int("sortOrder") ?: fallbackOrder,
        extra = o.without(ROUTINE_KEYS),
    )

    fun toJson(r: RoutineItem) = obj(
        r.extra,
        "routine" to str(r.routine),
        "exercise" to str(r.exercise),
        "targetSets" to num(r.targetSets),
        "targetWeight" to num(r.targetWeight),
        "targetReps" to num(r.targetReps),
        "restSeconds" to num(r.restSeconds),
        "sortOrder" to num(r.sortOrder),
    )

    private fun workout(o: JsonObject, index: Int): Workout {
        val date = o.string("date").orEmpty()
        val title = o.string("title").orEmpty()
        return Workout(
            // Records written by other tools may lack a syncId; derive a stable one.
            syncId = o.string("syncId")?.takeIf { it.isNotBlank() }
                ?: "import-${(date + "|" + title + "|" + index).hashCode().toUInt().toString(16)}",
            updatedAt = o.long("updatedAt") ?: 0,
            date = date,
            title = title,
            volume = o.double("volume"),
            setCount = o.int("setCount"),
            durationMinutes = o.int("durationMinutes"),
            gym = o.string("gym"),
            exerciseNames = o.array("exerciseNames").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            status = o.string("status") ?: Workout.STATUS_DONE,
            sets = o.objects("sets").mapIndexed { i, s -> set(s, i + 1) },
            extra = o.without(WORKOUT_KEYS),
        )
    }

    fun toJson(w: Workout) = obj(
        w.extra,
        "syncId" to str(w.syncId),
        "updatedAt" to num(w.updatedAt),
        "date" to str(w.date),
        "title" to str(w.title),
        "volume" to num(w.volume),
        "setCount" to num(w.setCount),
        "durationMinutes" to num(w.durationMinutes),
        "gym" to str(w.gym),
        "exerciseNames" to JsonArray(w.exerciseNames.map(::JsonPrimitive)),
        "status" to str(w.status),
        "sets" to JsonArray(w.sets.map(::toJson)),
    )

    private fun set(o: JsonObject, fallbackNumber: Int) = WSet(
        exercise = o.string("exercise").orEmpty(),
        setNumber = o.int("setNumber") ?: fallbackNumber,
        weight = o.double("weight"),
        reps = o.int("reps"),
        completed = o.bool("completed") ?: true,
        supersetGroup = o.string("supersetGroup"),
        extra = o.without(SET_KEYS),
    )

    fun toJson(s: WSet) = obj(
        s.extra,
        "exercise" to str(s.exercise),
        "setNumber" to num(s.setNumber),
        "weight" to num(s.weight),
        "reps" to num(s.reps),
        "completed" to JsonPrimitive(s.completed),
        "supersetGroup" to str(s.supersetGroup),
    )

    private fun recovery(o: JsonObject) = RecoveryRow(
        muscle = o.string("muscle").orEmpty(),
        availableAt = o.long("availableAt"),
        extra = o.without(RECOVERY_KEYS),
    )

    fun toJson(r: RecoveryRow) = obj(r.extra, "muscle" to str(r.muscle), "availableAt" to num(r.availableAt))

    private fun tombstone(o: JsonObject): Tombstone? {
        val id = o.string("syncId") ?: return null
        return Tombstone(id, o.long("deletedAt") ?: 0, o.without(TOMBSTONE_KEYS))
    }

    fun toJson(t: Tombstone) = obj(t.extra, "syncId" to str(t.syncId), "deletedAt" to num(t.deletedAt))

    // ---- helpers -------------------------------------------------------------------------

    private val DOC_KEYS = setOf(
        "version", "exportedAt", "exercises", "routines", "workouts", "templates",
        "recovery", "gyms", "settings", "tombstones",
    )
    private val EXERCISE_KEYS = setOf("id", "name", "baseline", "cue")
    private val ROUTINE_KEYS =
        setOf("routine", "exercise", "targetSets", "targetWeight", "targetReps", "restSeconds", "sortOrder")
    private val WORKOUT_KEYS = setOf(
        "syncId", "updatedAt", "date", "title", "volume", "setCount", "durationMinutes",
        "gym", "exerciseNames", "status", "sets",
    )
    private val SET_KEYS = setOf("exercise", "setNumber", "weight", "reps", "completed", "supersetGroup")
    private val RECOVERY_KEYS = setOf("muscle", "availableAt")
    private val TOMBSTONE_KEYS = setOf("syncId", "deletedAt")

    /** Known keys first, in the original app's order, then any extras. */
    private fun obj(extra: JsonObject, vararg pairs: Pair<String, JsonElement>): JsonObject {
        val m = LinkedHashMap<String, JsonElement>()
        pairs.forEach { (k, v) -> m[k] = v }
        extra.forEach { (k, v) -> if (k !in m) m[k] = v }
        return JsonObject(m)
    }

    private fun str(v: String?): JsonElement = if (v == null) JsonNull else JsonPrimitive(v)

    /** Whole numbers are written without a fractional part, matching the original writer. */
    private fun num(v: Number?): JsonElement = when (v) {
        null -> JsonNull
        is Double -> if (v.isFinite() && v == Math.rint(v) && kotlin.math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)
        is Float -> num(v.toDouble())
        else -> JsonPrimitive(v.toLong())
    }

    private fun JsonObject.without(keys: Set<String>) = JsonObject(filterKeys { it !in keys })

    private fun JsonObject.array(key: String): List<JsonElement> =
        (this[key] as? JsonArray)?.jsonArray ?: emptyList()

    private fun JsonObject.objects(key: String): List<JsonObject> = array(key).filterIsInstance<JsonObject>()

    private fun JsonObject.string(key: String): String? = stringOrNull(key)

    private fun JsonObject.double(key: String): Double? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.doubleOrNull ?: p.contentOrNull?.trim()?.toDoubleOrNull()
    }

    private fun JsonObject.long(key: String): Long? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.longOrNull ?: double(key)?.toLong()
    }

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    private fun JsonObject.bool(key: String): Boolean? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.booleanOrNull ?: when (p.contentOrNull) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }
}

internal fun JsonObject.stringOrNull(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.contentOrNull
}
