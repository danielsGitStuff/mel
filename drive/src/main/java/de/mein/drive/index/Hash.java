package de.mein.drive.index;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by xor on 7/10/16.
 */
public class Hash {

    public static String md5(File file) throws IOException {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");

            InputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int numRead;
            do {
                numRead = fis.read(buffer);
                if (numRead > 0) {
                    messageDigest.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
            fis.close();
            byte[] bytes = messageDigest.digest();
            return (new HexBinaryAdapter()).marshal(bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "exception :(";
        }
    }
}
