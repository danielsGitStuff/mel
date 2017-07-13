package de.mein.drive.bash;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Created by xor on 5/24/17.
 */
public class BashToolsException extends IOException {
    private final Stream<String> lines;

    public BashToolsException(Stream<String> lines) {
        this.lines = lines;
    }
}
