package de.mel.auth;

import de.mel.Lok;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.update.VersionAnswer;
import de.mel.update.VersionAnswerEntry;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class Serrtest {
    @Test
    public void test() throws Exception {
        List<String> mirrors = new ArrayList<>();
        mirrors.add("a.b");
        VersionAnswerEntry entry1 = new VersionAnswerEntry("variant1", "hash1", "commit", "version", 666L, mirrors);
        VersionAnswerEntry entry2 = new VersionAnswerEntry("variant2", "hash2", "commit", "version", 777L, mirrors);
        VersionAnswer answer = new VersionAnswer();
        answer.addEntry(entry1);
        answer.addEntry(entry2);
        String json = SerializableEntitySerializer.serialize(answer);
        Lok.debug(json);
        VersionAnswer des = (VersionAnswer) SerializableEntityDeserializer.deserialize(json);
        assertNotNull(des.getEntry("variant2"));
        Lok.debug("done");
    }
}
