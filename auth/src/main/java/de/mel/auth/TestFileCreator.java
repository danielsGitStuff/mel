package de.mel.auth;

import de.mel.Lok;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.core.serialize.serialize.tools.StringBuilder;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by xor on 1/2/17.
 */
public class TestFileCreator {
    /**
     * generates ~240kb
     *
     * @return
     */
    public static byte[] genBigFile() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Long i = 90000000000L; i < 90000020000L; i++)
            stringBuilder.append(i).append(',');
        return stringBuilder.toString().getBytes();
    }

    public static void saveFile(byte[] data, AbstractFile file) throws IOException {
        Lok.debug("TestFileCreator.saveFile: " + file.getAbsolutePath());
        AbstractFileWriter fos = file.writer();
//        fos.write(data);
        fos.close();
    }
}
