package de.mel.auth.socket.process.transfer;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.AbstractFileWriter;
import de.mel.auth.file.IFile;
import de.mel.auth.tools.N;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Container to tell the other side which file you want to receive. Files are identified by their hashes.
 * So you don't ask for a particular file but more for "send me a file like ..."
 * Created by xor on 1/5/17.
 */
public class FileTransferDetail implements SerializableEntity {

    @JsonIgnore
    private long position;
    private IFile file;
    private int streamId;
    private InputStream in;
    private AbstractFileWriter writer;
    private String hash;
    @JsonIgnore
    private boolean transferred = false;
    private FileTransferDoneListener transferDoneListener;
    private FileTransferFailedListener transferFailedListener;
    private FileTransferProgressListener transferProgressListener;

    private long start, end;
    private boolean e404 = false;

    public void openRead() throws FileNotFoundException {
        in = file.inputStream();
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

    public interface FileTransferProgressListener {
        void onFileTransferProgress(FileTransferDetail fileTransferDetail);
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

    public FileTransferDetail setTransferProgressListener(FileTransferProgressListener transferProgressListener) {
        this.transferProgressListener = transferProgressListener;
        return this;
    }

    public FileTransferDetail(IFile file, int streamId, long start, long end) {
        this.file = file;
        this.streamId = streamId;
        this.start = start;
        this.end = end;
        this.position = start;
        assertCheck();
    }

    public FileTransferDetail setHash(String hash) {
        this.hash = hash;
        return this;
    }

    public String getHash() {
        return hash;
    }

    public FileTransferDetail setFile(IFile file) {
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


    public IFile getFile() {
        return file;
    }

    public int getStreamId() {
        return streamId;
    }

    public void onReceived(long offset, byte[] data) throws IOException {
        if (writer == null)
            writer = file.writer();

        writer.append(data, offset);

        /*
         * TODO there is some kind of misinformation here.
         * You could possibly receive random blocks from all over the file
         * and this wouldn't notice.
         * Solving this is quite fiddly.
         */
        position = offset + data.length;
        if (position >= end) {
            assert position == end;
            N.r(() -> writer.close());
            transferred = true;
            if (transferDoneListener != null)
                transferDoneListener.onFileTransferDone(this);
        } else if (transferProgressListener != null) {
            transferProgressListener.onFileTransferProgress(this);
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
     * Where the input stream is, in bytes from the start of the file. The stream is read front to back, an offset is only a position in the file.
     */
    @JsonIgnore
    private long streamPosition = 0;

    /**
     * Reads up to 'length' bytes at 'offset', never beyond the end of this detail (the end of its range).
     * If the file or the range ends before, the result is shorter and the not filled bytes are counted; when the end is reached
     * the detail is transferred.
     *
     * @param offset where in the file to read. The stream continues where it was, so offsets have to increase.
     * @param length the most to read
     * @return the bytes that were read
     * @throws IOException
     */
    public FReadInfo readFile(long offset, int length) throws IOException {
        while (streamPosition < offset) {
            long skipped = in.skip(offset - streamPosition);
            if (skipped <= 0)
                break;
            streamPosition += skipped;
        }
        int wanted = (int) Math.max(0, Math.min(length, end - position));
        byte[] bytes = new byte[wanted];
        int readBytes = 0;
        while (readBytes < wanted) {
            int read = in.read(bytes, readBytes, wanted - readBytes);
            if (read == -1)
                break;
            readBytes += read;
        }
        streamPosition += readBytes;
        if (readBytes < wanted)
            bytes = Arrays.copyOf(bytes, readBytes);
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
