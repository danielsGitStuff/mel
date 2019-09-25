package de.mel.drive.transfer;

import de.mel.auth.service.Bootloader;
import de.mel.auth.socket.process.transfer.AbstractFileTransferDetailsPayload;

public class FileTransferDetailsPayload extends AbstractFileTransferDetailsPayload {
    public FileTransferDetailsPayload() {
        this.level = Bootloader.BootLevel.LONG;
    }
}
