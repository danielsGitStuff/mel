package de.mein.auth.service;

public class BootException extends Exception {
    public final Bootloader bootloader;

    public BootException(Bootloader bootloader, Exception e) {
        super(e);
        this.bootloader = bootloader;
    }

    public BootException(Bootloader bootloader, String s) {
        super(new Exception(s));
        this.bootloader = bootloader;
    }
}
