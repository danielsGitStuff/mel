package de.mein.auth;

import org.junit.Test;

import static org.junit.Assert.*;

import de.mein.auth.tools.MeinLogger;

/**
 * Created by xor on 9/9/17.
 */

public class Logger {
    @Test
    public void androidLogger() {
        MeinLogger.redirectSysOut(10);
        MeinLogger logger = MeinLogger.getInstance();
        for (int i = 0; i < 3; i++)
            Lok.debug(i);
        String[] lines = logger.getLines();
        assertEquals(3, lines.length);
        for (String line : lines)
            logger.toSysOut(line);
    }
}
