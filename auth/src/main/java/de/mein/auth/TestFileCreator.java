package de.mein.auth;

import de.mein.auth.file.AFile;
import de.mein.core.serialize.serialize.tools.StringBuilder;

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

    public static void saveFile(byte[] data, AFile file) throws IOException {
        System.out.println("TestFileCreator.saveFile: " + file.getAbsolutePath());
        FileOutputStream fos = file.outputStream();
        fos.write(data);
        fos.close();
    }
}
