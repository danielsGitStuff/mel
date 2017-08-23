package de.mein.auth.socket.process.transfer;

import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Created by xor on 1/5/17.
 */
public class FileTransferDetail implements SerializableEntity {

    @JsonIgnore
    private long position;
    private File file;
    private int streamId;
    private FileInputStream in;
    private FileOutputStream out;
    private Long fsId;
    private String hash;
    @JsonIgnore
    private boolean transferred = false;
    private FileTransferDoneListener transferDoneListener;
    private FileTransferFailedListener transferFailedListener;

    private long start, end;
    private boolean e404 = false;

    public void openRead() throws FileNotFoundException {
        in = new FileInputStream(file);
    }

    public FileTransferDetail setError(boolean hasError) {
        this.e404 = hasError;
        return this;
    }

    public void onFailed() {
        if (transferFailedListener != null)
            transferFailedListener.onFileTransferFailed(this);
    }

    public interface FileTransferDoneListener {
        void onFileTransferDone(FileTransferDetail fileTransferDetail);
    }

    public interface FileTransferFailedListener {
        void onFileTransferFailed(FileTransferDetail fileTransferDetail);
    }

    public FileTransferDetail() {

    }

    public FileTransferDetail setTransferDoneListener(FileTransferDoneListener transferDoneListener) {
        this.transferDoneListener = transferDoneListener;
        return this;
    }

    public FileTransferDetail setTransferFailedListener(FileTransferFailedListener transferFailedListener) {
        this.transferFailedListener = transferFailedListener;
        return this;
    }

    public FileTransferDetail(File file, int streamId, long start, long end) {
        this.file = file;
        this.streamId = streamId;
        this.start = start;
        this.end = end;
        this.position = start;
        assertCheck();
        //todo debug
        if (file.getName().equals("same1.txt"))
            System.err.println("MeinIsolatedFileProcess.addFilesReceiving.debug");
    }

    public FileTransferDetail setHash(String hash) {
        this.hash = hash;
        return this;
    }

    public String getHash() {
        return hash;
    }

    public Long getFsId() {
        return fsId;
    }

    public FileTransferDetail setFsId(Long fsId) {
        this.fsId = fsId;
        return this;
    }

    public FileTransferDetail setFile(File file) {
        this.file = file;
        assertCheck();
        return this;
    }

    private void assertCheck() {
        assert !file.isDirectory();
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public long getPosition() {
        return position;
    }


    public File getFile() {
        return file;
    }

    public int getStreamId() {
        return streamId;
    }

    public void onReceived(long offset, byte[] data) throws IOException {
        if (out == null)
            out = new FileOutputStream(file);
        FileChannel ch = out.getChannel();
        ch.position(offset);
        ch.write(ByteBuffer.wrap(data));
        /*
         * TODO there is some kind of misinformation here.
         * You could possibly receive random blocks from all over the file
         * and this wouldn't notice.
         * Solving this is quite fiddly.
         */
        position = offset + data.length;
        if (position >= end) {
            assert position == end;
            out.close();
            transferred = true;
            if (transferDoneListener != null)
                transferDoneListener.onFileTransferDone(this);
        }
    }

    public boolean transferred() {
        return transferred;
    }

    public static class FReadInfo {
        private byte[] bytes;
        private int notFilledBytes = 0;

        public byte[] getBytes() {
            return bytes;
        }

        public int getNotFilledBytes() {
            return notFilledBytes;
        }

        public FReadInfo(byte[] bytes, int notFilledBytes) {
            this.bytes = bytes;
            this.notFilledBytes = notFilledBytes;
        }
    }

    /**
     * tries to read 'length' bytes.
     * if file ends before this will return a shorter byte array,
     * set transferred to true.
     *
     * @param offset
     * @param length
     * @return the number of actual read bytes
     * @throws IOException
     */
    public FReadInfo readFile(long offset, int length) throws IOException {
        byte[] bytes = new byte[length];
        FileChannel ch = in.getChannel();
        ch.position(offset);
        int readBytes = ch.read(ByteBuffer.wrap(bytes));
        if (readBytes == -1) {
            bytes = new byte[0];
        } else if (readBytes < length) {
            bytes = Arrays.copyOf(bytes, readBytes);
        }
        position += readBytes;
        if (position == end) {
            in.close();
            transferred = true;
        }
        return new FReadInfo(bytes, length - readBytes);
    }

    public boolean hasError() {
        return e404;
    }
}
