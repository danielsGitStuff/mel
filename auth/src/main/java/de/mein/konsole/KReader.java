package de.mein.konsole;

public interface KReader<T extends KResult> {
    void handle(T result, String[] args) throws ParseArgumentException;
}
