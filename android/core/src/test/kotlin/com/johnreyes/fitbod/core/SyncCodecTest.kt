package com.johnreyes.fitbod.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncCodecTest {
    private fun sample() = javaClass.getResource("/sample-sync.json")!!.readText()

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test
    fun roundTripIsLossless() {
        val text = sample()
        assertEquals(parse(text), parse(SyncCodec.encode(SyncCodec.decode(text))))
    }

    /** Set FITBOD_SYNC_FILE to a real export to check it survives a round trip too. */
    @Test
    fun realFileRoundTripIfProvided() {
        val path = System.getenv("FITBOD_SYNC_FILE") ?: return
        val text = File(path).readText()
        assertEquals(parse(text), parse(SyncCodec.encode(SyncCodec.decode(text))))
    }

    @Test
    fun decodesKnownFields() {
        val doc = SyncCodec.decode(sample())
        assertEquals(2, doc.version)
        assertEquals("Pec Deck / Machine Fly", doc.exercises[1].name)
        assertEquals(listOf("Set A — Full Body", "Set B — Full Body"), Edits.routineNames(doc))
        assertNull(doc.routines[1].restSeconds)
        val w = doc.workouts[1]
        assertEquals("Base Gym", w.gym)
        assertEquals("B", w.sets[1].supersetGroup)
        assertEquals(100.5, w.sets[2].weight)
        assertEquals(90, doc.settings.defaultRestSeconds)
        assertEquals("lbs", doc.settings.units)
        assertTrue(doc.extra.containsKey("futureTopLevel"))
        assertTrue(w.extra.containsKey("plan"))
    }

    @Test
    fun wholeNumbersAreWrittenWithoutDecimals() {
        val out = SyncCodec.encode(SyncCodec.decode(sample()))
        assertTrue("\"targetWeight\": 100," in out, out)
        assertTrue("\"weight\": 100.5" in out)
    }

    @Test
    fun toleratesMissingSyncIdAndStringNumbers() {
        val doc = SyncCodec.decode(
            """{"workouts":[{"date":"2026-10-30","title":"Logged by Merlin",
               "sets":[{"exercise":"Push Up","weight":"0","reps":"15"}]}]}""",
        )
        val w = doc.workouts.single()
        assertTrue(w.syncId.startsWith("import-"))
        assertEquals(SyncCodec.decode("""{"workouts":[{"date":"2026-10-30","title":"Logged by Merlin"}]}""").workouts[0].syncId, w.syncId)
        assertEquals(15, w.sets[0].reps)
        assertEquals("done", w.status)
    }
}
