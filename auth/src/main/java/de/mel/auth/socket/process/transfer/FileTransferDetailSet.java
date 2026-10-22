package de.mel.auth.socket.process.transfer;

import de.mel.auth.data.ServicePayload;
import de.mel.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xor on 1/6/17.
 */
public class FileTransferDetailSet implements SerializableEntity {
    private List<FileTransferDetail> details = new ArrayList<>();
    private String serviceUuid;
    /**
     * the isolated connection the asking side waits on for the files: they have to come there (null from partners that do not say)
     */
    private String isolatedUuid;

    public String getIsolatedUuid() {
        return isolatedUuid;
    }

    public FileTransferDetailSet setIsolatedUuid(String isolatedUuid) {
        this.isolatedUuid = isolatedUuid;
        return this;
    }

    public List<FileTransferDetail> getDetails() {
        return details;
    }

    public FileTransferDetailSet add(FileTransferDetail detail) {
        details.add(detail);
        return this;
    }

    public FileTransferDetailSet setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
        return this;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }
}
