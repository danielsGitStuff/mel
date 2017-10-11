package de.mein.contacts;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.mein.contacts.data.db.Contact;
import de.mein.contacts.data.db.ContactPhone;
import de.mein.core.serialize.SerializableEntity;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializer;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactory;
import de.mein.core.serialize.serialize.fieldserializer.FieldSerializerFactoryRepository;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializerFactory;
import de.mein.core.serialize.serialize.reflection.FieldAnalyzer;
import de.mein.sql.Pair;
import de.mein.sql.serialize.PairCollectionSerializer;
import de.mein.sql.serialize.PairCollectionSerializerFactory;
import de.mein.sql.serialize.PairSerializerFactory;

/**
 * Created by xor on 10/11/17.
 */

public class SerializationTest {
    @Test
    public void ser() throws Exception {
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairSerializerFactory.getInstance());
        FieldSerializerFactoryRepository.addAvailableSerializerFactory(PairCollectionSerializerFactory.getInstance());
        Contact contact = new Contact();
        ContactPhone phone = new ContactPhone();
        for (Integer i = 0; i < 15; i++)
            phone.setValue(i, i.toString());
        contact.addPhone(phone);
        contact.getHash().v("hurrdurr");
        String json = SerializableEntitySerializer.serialize(contact);
        List<Field> fields = FieldAnalyzer.collectFields(ContactPhone.class);
        Field dataColsField = fields.stream().filter(field -> field.getName().equals("dataCols")).findFirst().get();
        boolean collection = FieldAnalyzer.isCollectionClass(dataColsField.getType());
        boolean pairCollection = FieldAnalyzer.isCollectionOfClass(dataColsField, Pair.class);
        System.out.println(json);
        System.out.println("SerializationTest.ser");

    }

}
