package de.mel.core.serialize.serialize.tools;

import org.junit.Test;

import static org.junit.Assert.*;

public class NumberTransformerTest {

    @Test
    public void cast() {
        Object lonk = NumberTransformer.forType(Long.class).cast(1);
        assertEquals(1L, lonk);
    }

    @Test
    public void forType() {
        NumberTransformer transformer = NumberTransformer.forType(Long.class);
        assertNotNull(transformer);
    }
}