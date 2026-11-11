package de.mel.auth;

import de.mel.Lok;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.auth.file.StandardFileWriter;
import de.mel.core.serialize.serialize.tools.StringBuilder;

import java.io.File;
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

    public static void saveFile(byte[] data, File file) throws Exception {
        Lok.debug("TestFileCreator.saveFile: " + file.getAbsolutePath());
        AbstractFileWriter fos = new StandardFileWriter(file);
        fos.append(data, 0L);
        fos.close();
    }
}
