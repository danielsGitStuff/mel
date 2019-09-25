package de.mel.auth.socket.process.transfer;

/**
 * Created by xor on 1/2/17.
 */
public class Block {
    private byte[] bytes;
    private final int streamId;
    private int firstByteToProcessIndex;
    private Byte firstByteToProcess;

    public Block(int streamId) {
        this.streamId = streamId;
        this.firstByteToProcessIndex = 0;
    }

    public byte getFirstByteToProcess() {
        return firstByteToProcess;
    }

    public Block setBytes(byte[] bytes) {
        this.bytes = bytes;
        setByte();
        return this;
    }

    public Block setFirstByteToProcessIndex(int firstByteToProcessIndex) {
        this.firstByteToProcessIndex = firstByteToProcessIndex;
        setByte();
        return this;
    }

    private void setByte() {
        if (firstByteToProcessIndex < bytes.length)
            this.firstByteToProcess = bytes[firstByteToProcessIndex];
        else
            firstByteToProcess = null;

    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getFirstByteToProcessIndex() {
        return firstByteToProcessIndex;
    }
}
