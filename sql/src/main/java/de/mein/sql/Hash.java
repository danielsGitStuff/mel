package de.mein.sql;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Created by xor on 7/10/16.
 */
public class Hash {

    public static String md5(File file) {
        MessageDigest messageDigest;
        System.out.println("Hash.md5: " + file.getAbsolutePath());
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
            return bytesToString(bytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "exception :(";
        }
    }

    public static String bytesToString(byte[] bytes) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            if ((0xff & bytes[i]) < 0x10) {
                hexString.append("0"
                        + Integer.toHexString((0xFF & bytes[i])));
            } else {
                hexString.append(Integer.toHexString(0xFF & bytes[i]));
            }
        }
        return hexString.toString().toLowerCase();
    }

    public static String md5(byte[] bytes) throws IOException {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(bytes);
            byte[] hash = messageDigest.digest();
            return bytesToString(hash);
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
            return bytesToString(hash);
        } catch (Exception e) {
            e.printStackTrace();
            return "exception :(";
        }
    }

}
