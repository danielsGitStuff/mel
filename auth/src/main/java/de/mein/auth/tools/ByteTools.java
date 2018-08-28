package de.mein.auth.tools;

/**
 * Created by xor on 12/30/16.
 */
public class ByteTools {
    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    public static long bytesToLong(byte[] b, int offset) {
        long result = 0;
        int max = offset + 8;
        for (int i = offset; i < max; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    /**
     * @param target
     * @param sources
     * @return index of last byte
     */
    public static int fill(byte[] target, byte[]... sources) {
        return fill(target, 0, sources);
    }

    /**
     * @param target
     * @param offset
     * @param sources
     * @return index of last byte
     */
    public static int fill(byte[] target, int offset, byte[]... sources) {
        for (byte[] source : sources) {
            for (byte b : source) {
                if (offset == target.length)
                    return offset;
                target[offset] = b;
                offset++;
            }
        }
        return offset;
    }

    public static byte[] intToBytes(int i) {
        return new byte[]{
                (byte) (i >> 24),
                (byte) (i >> 16),
                (byte) (i >> 8),
                (byte) (i)
        };
    }

    public static int bytesToInt(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | bytes[3] & 0xFF;
    }

    public static int bytesToInt(byte[] bytes, int offset) {
        return bytes[offset] << 24 | (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
    }
}
