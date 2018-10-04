package de.mein.contacts;

import de.mein.Lok;
import de.mein.contacts.data.db.ContactAppendix;
import org.junit.Test;

import de.mein.contacts.data.db.Contact;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.sql.Pair;
import de.mein.sql.deserialize.PairCollectionDeserializerFactory;
import de.mein.sql.deserialize.PairDeserializerFactory;
import de.mein.sql.serialize.PairCollectionSerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;

import static org.junit.Assert.*;

/**
 * Created by xor on 10/11/17.
 */

public class SerializationTest {
    @Test
    public void serialize() throws Exception {
        serializeImpl();
    }

    public String serializeImpl() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        Contact contact = new Contact();
        ContactAppendix appendix = new ContactAppendix();
        for (Integer i = 0; i < 14; i++) {
            if (i != 5)
                appendix.setValue(i, i.toString());
        }
        appendix.getBlob().v(new byte[]{1,2,3,4});
        contact.addAppendix(appendix);
        contact.getHash().v("hurrdurr");
        String json = SerializableEntitySerializer.serialize(contact);
        Lok.debug(json);
        assertEquals("{\"$id\":1,\"__type\":\"de.mein.contacts.data.db.Contact\",\"appendices\":[{\"$id\":2,\"__type\":\"de.mein.contacts.data.db.ContactAppendix\",\"blob\":\"AQIDBA==\",\"dataCols\":[\"0\",\"1\",\"2\",\"3\",\"4\",null,\"6\",\"7\",\"8\",\"9\",\"10\",\"11\",\"12\",\"13\"]}],\"hash\":\"hurrdurr\"}", json);
        return json;
    }

    @Test
    public void deserialize() throws Exception {
        String json = serializeImpl();
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        Contact contact = (Contact) SerializableEntityDeserializer.deserialize(json);
        ContactAppendix phone = contact.getAppendices().get(0);
        Pair<String> pair3 = phone.getDataCols().get(3);
        Pair<String> pair5 = phone.getDataCols().get(5);
        assertEquals("data4", pair3.k());
        assertEquals("3", pair3.v());
        assertNull(pair5.v());
        Lok.debug("SerializationTest.deserialize");
    }

}
