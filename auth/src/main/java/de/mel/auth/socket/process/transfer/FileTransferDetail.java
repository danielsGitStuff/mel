package de.mel.auth.socket.process.transfer;

import de.mel.Lok;
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
    private volatile InputStream in;
    private volatile AbstractFileWriter writer;
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

    /**
     * The transfer cannot be completed. Tells the listener, once.
     */
    public void onFailed() {
        abort();
        if (failed)
            return;
        failed = true;
        if (transferFailedListener != null)
            transferFailedListener.onFileTransferFailed(this);
    }

    @JsonIgnore
    private boolean failed = false;

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

    /**
     * Opens the file for the data that is going to arrive: a new one for a transfer from the start, otherwise the existing one, continued where this detail starts.
     * Is done on the first {@link #onReceived(long, byte[])} if you did not do it before. Doing it before tells you early that the file cannot be written.
     *
     * @throws IOException if the file cannot be written from {@link #getStart()} on. The caller can start over from 0.
     */
    public void openWrite() throws IOException {
        if (writer != null)
            return;
        AbstractFileWriter opened = start == 0L ? file.writer() : file.writer(start);
        if (opened == null)
            throw new IOException("cannot write " + file.getName() + " from " + start);
        writer = opened;
    }

    /**
     * Closes the file if it is open. Whatever was written stays.
     */
    public void abort() {
        AbstractFileWriter toClose = writer;
        writer = null;
        if (toClose != null) {
            try {
                toClose.close();
            } catch (Exception e) {
                Lok.error("could not close " + file.getName() + ": " + e);
            }
        }
    }

    /**
     * Takes a piece of the file. The pieces have to come in order, one after the other, from {@link #getStart()} on, and not go beyond {@link #getEnd()}.
     * Whatever is sent otherwise is not the file we asked for.
     *
     * @throws IOException if the piece does not belong here or cannot be written. The detail is not usable afterwards, abort it.
     */
    public void onReceived(long offset, byte[] data) throws IOException {
        if (transferred)
            throw new IOException("already got all of " + file.getName());
        if (offset != position)
            throw new IOException("expected " + file.getName() + " at " + position + " but got a piece at " + offset);
        if (data.length > end - position)
            throw new IOException("got " + data.length + " bytes at " + offset + " but " + file.getName() + " ends at " + end);
        openWrite();
        writer.append(data, offset);
        position = offset + data.length;
        if (position == end) {
            AbstractFileWriter toClose = writer;
            writer = null;
            try {
                toClose.close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
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
     * If the file ends before the range does, the result is shorter and the bytes that are missing are counted as not filled; when the end of the range is reached
     * the detail is transferred.
     *
     * @param offset where in the file to read. The stream continues where it was, so offsets have to increase.
     * @param length the most to read
     * @return the bytes that were read
     * @throws IOException
     */
    public FReadInfo readFile(long offset, int length) throws IOException {
        InputStream in = this.in;
        if (in == null)
            throw new IOException("not open for reading: " + file.getName());
        while (streamPosition < offset) {
            long skipped = in.skip(offset - streamPosition);
            if (skipped <= 0)
                throw new IOException("cannot get to " + offset + " in " + file.getName() + ", got to " + streamPosition);
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
        return new FReadInfo(bytes, wanted - readBytes);
    }

    /**
     * Lets go of the file that is read, after the last piece was sent or when it will not be sent any more.
     */
    public void abortRead() {
        InputStream toClose = in;
        in = null;
        if (toClose != null) {
            try {
                toClose.close();
            } catch (IOException e) {
                Lok.error("could not close " + file.getName() + ": " + e);
            }
        }
    }

    public boolean hasError() {
        return e404;
    }
}
