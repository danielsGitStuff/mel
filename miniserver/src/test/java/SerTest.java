import de.mel.Lok;
import de.mel.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mel.update.VersionAnswer;
import org.junit.Test;

public class SerTest {
    @Test
    public void serializeVersion() throws Exception {
        VersionAnswer answer = new VersionAnswer();
        answer.addEntry("hash", "name", "commit", "666L", 777L);
        String json = SerializableEntitySerializer.serialize(answer);
        Lok.debug(json);
    }
}
