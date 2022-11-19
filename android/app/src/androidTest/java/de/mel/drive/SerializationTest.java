package de.mel.drive;

import androidx.test.runner.AndroidJUnit4;

import de.mel.Lok;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;

/**
 * Created by xor on 3/8/17.
 */
@RunWith(AndroidJUnit4.class)
public class SerializationTest {
    public static class A implements SerializableEntity{
        public List<String> strings = new ArrayList<>();
    }
    @Test
    public void primitiveCollectionTest() throws Exception {
        A obj = new A();
        obj.strings.add("AAA");
        obj.strings.add("BBB");
        String json = SerializableEntitySerializer.serialize(obj);
        Lok.debug(json);
    }
}
