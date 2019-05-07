package de.mein.drive.transfer;

import de.mein.auth.service.Bootloader;
import de.mein.auth.socket.process.transfer.AbstractFileTransferDetailsPayload;

public class FileTransferDetailsPayload extends AbstractFileTransferDetailsPayload {
    public FileTransferDetailsPayload() {
        this.level = Bootloader.BootLevel.LONG;
    }
}
