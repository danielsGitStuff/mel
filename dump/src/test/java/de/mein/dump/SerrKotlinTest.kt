package de.mein.dump

import de.mein.Lok
import de.mein.core.serialize.SerializableEntity
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import org.junit.Before
import org.junit.Test

import org.junit.Assert.*

class SerrKotlinTest {

    var dumpJob: DumpJob? = null

    @Before
    fun setUp() {
        dumpJob = DumpJob()
    }

    @Test
    fun serialize() {
        dumpJob!!.string = "nein"
        val json = SerializableEntitySerializer.serialize(dumpJob)
        Lok.debug(json)
        val deserialized = SerializableEntityDeserializer.deserialize(json) as DumpJob
        assertEquals(dumpJob!!.string, deserialized.string)
        Lok.debug("done")

    }
}