package de.mein.contacts;

import org.junit.Test;

import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactPhone;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
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
        ContactPhone phone = new ContactPhone();
        for (Integer i = 0; i < 15; i++)
            phone.setValue(i, i.toString());
        contact.addPhone(phone);
        contact.getHash().v("hurrdurr");
        String json = SerializableEntitySerializer.serialize(contact);
        System.out.println(json);
        assertEquals("{\"$id\":1,\"__type\":\"de.mein.contacts.data.db.Contact\",\"phones\":[{\"$id\":2,\"__type\":\"de.mein.contacts.data.db.ContactPhone\",\"dataCols\":[\"0\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\",\"12\",\"13\",\"14\"]}],\"hash\":\"hurrdurr\"}", json);
        return json;
    }

    @Test
    public void deserialize() throws Exception {
        String json = serializeImpl();
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        Contact contact = (Contact) SerializableEntityDeserializer.deserialize(json);
        System.out.println("SerializationTest.deserialize");
    }

}
