package de.mein.sql;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 10/3/17.
 */

public class MD5er {
    private MessageDigest digest;
    private String hash;

    public MD5er() {
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public String digest() {
        hash = Hash.bytesToString(digest.digest());
        return hash;
    }

    public MD5er hash(Object o) {
        if (hash != null)
            return this;
        if (o != null) {
            if (o instanceof String) {
                String s = (String) o;
                digest.update(s.getBytes());
            } else if (o instanceof Long) {
                digest.update(ByteBuffer.allocate(8).putLong((Long) o).array());
            } else if (o instanceof Integer) {
                digest.update(ByteBuffer.allocate(4).putInt((Integer) o).array());
            } else if (o instanceof Short) {
                digest.update(ByteBuffer.allocate(2).putShort((Short) o).array());
            } else if (o instanceof Float) {
                digest.update(ByteBuffer.allocate(4).putFloat((Float) o).array());
            } else if (o instanceof Double) {
                digest.update(ByteBuffer.allocate(8).putDouble((Double) o).array());
            } else if (o instanceof Character) {
                digest.update(ByteBuffer.allocate(2).putChar((Character) o).array());
            } else if (o instanceof byte[]) {
                byte[] bytes = (byte[]) o;
                digest.update(bytes);
            }
        } else
            digest.update("null".getBytes());
        return this;
    }
}
