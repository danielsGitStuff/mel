package de.mein.core;

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

    public static String md5(byte[] bytes) throws IOException {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(bytes, 0, bytes.length);
            byte[] hash = messageDigest.digest();
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                if ((0xff & hash[i]) < 0x10) {
                    hexString.append("0"
                            + Integer.toHexString((0xFF & hash[i])));
                } else {
                    hexString.append(Integer.toHexString(0xFF & hash[i]));
                }
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "exception :(";
        }
    }

    public static String sha256(byte[] bytes) throws IOException {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA256");
            messageDigest.update(bytes, 0, bytes.length);
            byte[] hash = messageDigest.digest();
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                if ((0xff & hash[i]) < 0x10) {
                    hexString.append("0"
                            + Integer.toHexString((0xFF & hash[i])));
                } else {
                    hexString.append(Integer.toHexString(0xFF & hash[i]));
                }
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "exception :(";
        }
    }

}
