package de.mein.drive.index;

import java.util.stream.Stream;

/**
 * Created by xor on 5/24/17.
 */
public class BashToolsException extends Exception {
    private final Stream<String> lines;

    public BashToolsException(Stream<String> lines) {
        this.lines = lines;
    }
}
