package de.mein.drive.serialization;


import de.mein.Lok;
import de.mein.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import de.mein.core.serialize.exceptions.JsonDeserializationException;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.core.serialize.serialize.fieldserializer.entity.SerializableEntitySerializer;
import de.mein.core.serialize.serialize.tools.OTimer;
import de.mein.drive.sql.FsDirectory;
import de.mein.drive.sql.FsFile;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;

/**
 * Created by xor on 10/28/15.
 */
public class SerializationPerformanceTest {
//    @Test
    public void testPerformance() throws JsonSerializationException, FileNotFoundException, UnsupportedEncodingException, JsonDeserializationException {
        SerializableEntitySerializer serializer = new SerializableEntitySerializer();
        OTimer timer = new OTimer("serialization!");
        FsDirectory root = new FsDirectory();
        Long id = Long.valueOf(Long.MAX_VALUE) + 2;
        root.getId().v(id);
        root.getName().v("[Root]");
        Long count = 1l;
        for (int i = 0; i < 100; i++) {
            FsDirectory subDir = new FsDirectory();
            subDir.getId().v(count++);
            subDir.getName().v("subDir" + count);
            subDir.setParent(root);
            subDir.getParentId().v(root.getId());
            root.addSubDirectory(subDir);
        }
        count = 1l;
        for (FsDirectory dir : root.getSubDirectories()) {
            for (int i = 0; i < 500; i++) {
                FsFile f = new FsFile();
                f.getId().v(count++);
                f.getName().v("file" + count);
                f.getContentHash().v("MD5GOESHEREMD5GOESHEREMD5GOESHERE");
                f.getParentId().v(dir.getId());
                dir.addFile(f);
            }
        }
        serializer.setEntity(root);
        serializer.setTraversalDepth(4);
        timer.start();
        String json = serializer.JSON();
        timer.stop().print();
        SerializableEntityDeserializer deserializer = new SerializableEntityDeserializer();
        timer.reset().start();
        Object des = deserializer.deserialize(json);
        timer.stop().print();
        //Lok.debug(de.mein.json.json);
    }

//    @Test
    public void repeat() throws FileNotFoundException, JsonDeserializationException, JsonSerializationException, UnsupportedEncodingException {
        Lok.debug("SerializationPerformanceTest.repeat");
        testPerformance();
        Lok.debug("SerializationPerformanceTest.repeat.end");
    }
}
