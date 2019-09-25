package de.mel.contacts;

import de.mel.Lok;
import de.mel.auth.data.ServicePayload;
import de.mel.contacts.data.db.AppendixWrapper;
import de.mel.contacts.data.db.ContactAppendix;

import org.junit.Before;
import org.junit.Test;

import de.mel.contacts.data.db.Contact;
import de.mel.contacts.data.db.PhoneBook;
import de.mel.contacts.data.db.PhoneBookWrapper;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.sql.Pair;
import de.mel.sql.deserialize.PairCollectionDeserializerFactory;
import de.mel.sql.deserialize.PairDeserializerFactory;
import de.mel.sql.serialize.PairCollectionSerializerFactory;
import de.mel.sql.serialize.PairSerializerFactory;

import static org.junit.Assert.*;

/**
 * todo re-enable the tests. this requires work especially on the android side
 * Created by xor on 10/11/17.
 */

public class SerializationTest {
    public static class B implements SerializableEntity {
        String name = "name";
    }

    public static class A extends ServicePayload {
        B b;
    }

    @Before
    public void before() {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairDeserializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableDeserializerFactory(PairCollectionDeserializerFactory.getInstance());
    }

    @Test
    public void serialize2() throws Exception {
        B b = new B();
        A a = new A();
        a.b = b;
        String json = SerializableEntitySerializer.serialize(a);
        A des = (A) SerializableEntityDeserializer.deserialize(json);
        assertEquals(a.b.name, des.b.name);
    }

    @Test
    public void serialize() throws Exception {
        ContactAppendix appendix = new ContactAppendix();
        Contact contact = new Contact();
        contact.addAppendix(appendix);
        PhoneBook phoneBook = new PhoneBook();
        phoneBook.addContact(contact);
        PhoneBookWrapper wrapper = new PhoneBookWrapper(phoneBook);
        String json = SerializableEntitySerializer.serialize(wrapper);
        PhoneBookWrapper des = (PhoneBookWrapper) SerializableEntityDeserializer.deserialize(json);
        assertEquals(wrapper.getPhoneBook().toString(), des.getPhoneBook().toString());
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
        appendix.getBlob().v(new byte[]{1, 2, 3, 4});
        contact.addAppendix(appendix);
        contact.getHash().v("hurrdurr");
        String json = SerializableEntitySerializer.serialize(contact);
        Lok.debug(json);
        Lok.debug("");
        Contact des = (Contact) SerializableEntityDeserializer.deserialize(json);
        String expected = "{\"$id\":1,\"__type\":\"de.mel.contacts.data.db.Contact\",\"appendices\":[{\"$id\":2,\"__type\":\"de.mel.contacts.data.db.ContactAppendix\",\"blob\":\"AQIDBA==\",\"dataCols\":[\"0\",\"1\",\"2\",\"3\",\"4\",null,\"6\",\"7\",\"8\",\"9\",\"10\",\"11\",\"12\",\"13\"]}],\"hash\":\"hurrdurr\"}";
        Lok.debug(expected);
        assertEquals(expected, json);
        return json;
    }

    //    @Test
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
