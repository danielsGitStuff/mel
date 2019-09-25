package de.mel.sql.transform;

import org.junit.Test;

import static org.junit.Assert.*;

public class NumberTransformerTest {

    private NumberTransformer nt;

    @Test
    public void cast2Box() {
        Long boxed = 20L;
        nt = NumberTransformer.forType(int.class);
        Object primitiveObject = nt.cast(boxed);
        assertEquals(Integer.class, primitiveObject.getClass());
    }

    @Test
    public void cast2primitive() {
        Long boxed = 20L;
        nt = NumberTransformer.forType(int.class);
        int primitive = (int) nt.cast(boxed);
        Object primitiveObject = primitive;
        assertTrue(Integer.class.equals(primitiveObject.getClass()));
    }
}