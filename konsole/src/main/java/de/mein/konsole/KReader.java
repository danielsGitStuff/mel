package de.mein.konsole;

import de.mein.KResult;

public interface KReader<T extends KResult> {
    /**
     * Digest the arguments specified after your pre-defined attribute here.
     * You will only get all arguments between this attribute and the next one.
     * @param result the result object of the {@link Konsole}
     * @param args all arguments you have to handle
     * @throws Konsole.ParseArgumentException something is wrong (eg: file does not exist)
     */
    void handle(T result, String[] args) throws Konsole.ParseArgumentException;
}
