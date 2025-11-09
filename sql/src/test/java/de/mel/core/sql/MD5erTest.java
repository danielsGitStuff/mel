package de.mel.core.sql;


import de.mel.sql.MD5er;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Created by xor on 10/10/17.
 */

public class MD5erTest {
    @Test
    public void equals() {
        MD5er md1 = new MD5er();
        MD5er md2 = new MD5er();
        //byte[] bytes = new byte[]{1, 2, 3};
        String bytes = null;
        String h1 = md1.hash(bytes).digest();
        String h2 = md2.hash(bytes).digest();
        assertEquals(h1, h2);
    }
}
