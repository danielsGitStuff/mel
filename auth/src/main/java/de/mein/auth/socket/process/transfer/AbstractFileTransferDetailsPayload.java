package de.mein.auth.socket.process.transfer;

import de.mein.auth.data.ServicePayload;

public abstract class AbstractFileTransferDetailsPayload extends ServicePayload {
    private FileTransferDetailSet fileTransferDetailSet;

    public void setFileTransferDetailSet(FileTransferDetailSet fileTransferDetailSet) {
        this.fileTransferDetailSet = fileTransferDetailSet;
    }

    public FileTransferDetailSet getFileTransferDetailSet() {
        return fileTransferDetailSet;
    }
}
