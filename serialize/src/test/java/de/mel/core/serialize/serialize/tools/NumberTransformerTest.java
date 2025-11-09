package de.mel.core.serialize.serialize.tools;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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