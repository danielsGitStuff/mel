package de.mein.auth;

import de.mein.auth.tools.ByteTools;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by xor on 12/30/16.
 */
public class ByteToolsTest {
    @Test
    public void long2byte() {
        Long l = 500L;
        Long ll = ByteTools.bytesToLong(ByteTools.longToBytes(l));
        assertEquals(l, ll);
    }

    @Test
    public void int2byte() {
        int i = 500;
        int ii = ByteTools.bytesToInt(ByteTools.intToBytes(i));
        assertEquals(i, ii);
    }

    @Test
    public void fill() {
        byte[] arr = new byte[12];
        final int offset = 4;
        ByteTools.fill(arr, offset, ByteTools.longToBytes(7));
        assertEquals(7L, arr[11]);
    }

    @Test
    public void read() {
        byte[] arr = new byte[12];
        final int offset = 4;
        ByteTools.fill(arr, offset, ByteTools.longToBytes(77777777));
        long read = ByteTools.bytesToLong(arr, offset);
        assertEquals(77777777L, read);
    }
    @Test
    public void read2(){
        byte[] arr = new byte[]{0,0,0,0,0,0,0,0,-96,21};
        long read = ByteTools.bytesToLong(arr,2);
        assertEquals(40981, read);
    }
}
