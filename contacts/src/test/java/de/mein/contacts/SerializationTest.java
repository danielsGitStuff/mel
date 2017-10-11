package de.mein.contacts;

import org.junit.Test;

import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactPhone;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;

/**
 * Created by xor on 10/11/17.
 */

public class SerializationTest {
    @Test
    public void ser() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        Contact contact = new Contact();
        ContactPhone phone = new ContactPhone();
        for (Integer i = 0; i < 15; i++)
            phone.setValue(i, i.toString());
        contact.addPhone(phone);
        contact.getHash().v("hurrdurr");
        String json = SerializableEntitySerializer.serialize(contact);
        System.out.println(json);
    }
}
