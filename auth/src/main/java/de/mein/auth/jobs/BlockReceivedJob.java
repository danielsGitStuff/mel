package de.mein.auth.jobs;

/**
 * Created by xor on 12/16/16.
 */
public class BlockReceivedJob extends Job {
    private byte[] block;

    public BlockReceivedJob setBlock(byte[] block) {
        this.block = block;
        return this;
    }

    public byte[] getBlock() {
        return block;
    }
}
