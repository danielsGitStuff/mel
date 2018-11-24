package de.mein.contacts

import de.mein.Lok
import de.mein.contacts.data.db.ContactAppendix
import org.junit.Test

import de.mein.contacts.data.db.Contact
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer
import de.mein.sql.Pair
import de.mein.sql.deserialize.PairCollectionDeserializerFactory
import de.mein.sql.deserialize.PairDeserializerFactory
import de.mein.sql.serialize.PairCollectionSerializerFactory
import de.mein.sql.serialize.PairSerializerFactory

import org.junit.Assert.*

/**
 * Created by xor on 10/11/17.
 */

class SerializationTest {
    @Test
    @Throws(Exception::class)
    fun serialize() {
        serializeImpl()
    }

    @Throws(Exception::class)
    fun serializeImpl(): String {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance())
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance())
        val contact = Contact()
        val appendix = ContactAppendix()
        for (i in 0..13) {
            if (i != 5)
                appendix.setValue(i, i.toString())
        }
        appendix.blob.v(byteArrayOf(1, 2, 3, 4))
        contact.addAppendix(appendix)
        contact.hash.v("hurrdurr")
        val json = SerializableEntitySerializer.serialize(contact)
        Lok.debug(json)
        assertEquals("{\"\$id\":1,\"__type\":\"de.mein.contacts.data.db.Contact\",\"appendices\":[{\"\$id\":2,\"__type\":\"de.mein.contacts.data.db.ContactAppendix\",\"blob\":\"AQIDBA==\",\"dataCols\":[\"0\",\"1\",\"2\",\"3\",\"4\",null,\"6\",\"7\",\"8\",\"9\",\"10\",\"11\",\"12\",\"13\"]}],\"hash\":\"hurrdurr\"}", json)
        return json
    }

    @Test
    @Throws(Exception::class)
    fun deserialize() {
        val json = serializeImpl()
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance())
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance())
        val contact = SerializableEntityDeserializer.deserialize(json) as Contact
        val phone = contact.appendices[0]
        val pair3 = phone.dataCols[3]
        val pair5 = phone.dataCols[5]
        assertEquals("data4", pair3.k())
        assertEquals("3", pair3.v())
        assertNull(pair5.v())
        Lok.debug("SerializationTest.deserialize")
    }

}
