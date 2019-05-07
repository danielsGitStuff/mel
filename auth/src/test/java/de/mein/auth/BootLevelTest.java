package de.mein.auth;

import org.junit.Test;

import de.mein.auth.service.Bootloader;

import static org.junit.Assert.*;
public class BootLevelTest {
    @Test
    public void greaterOrEqual(){
        Bootloader.BootLevel zero = Bootloader.BootLevel.NONE;
        Bootloader.BootLevel one = Bootloader.BootLevel.SHORT;
        assertTrue(one.greaterOrEqual(zero));
        assertTrue(one.greaterOrEqual(one));
    }
}
