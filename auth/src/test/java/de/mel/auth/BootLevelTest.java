package de.mel.auth;


import de.mel.auth.service.Bootloader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BootLevelTest {
    @Test
    public void greaterOrEqual() {
        Bootloader.BootLevel zero = Bootloader.BootLevel.NONE;
        Bootloader.BootLevel one = Bootloader.BootLevel.SHORT;
        assertTrue(one.greaterOrEqual(zero));
        assertTrue(one.greaterOrEqual(one));
    }
}
