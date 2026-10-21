package de.mel.auth.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * What travels over a Mel socket: frames of <code>type (1 byte) | length (4 bytes, big endian) | payload</code>.
 * Messages are JSON in UTF-8, blocks are binary. A frame is never longer than {@link #MAX_FRAME}, the receiver holds a whole frame in memory.
 */
public final class FrameCodec {
    public static final byte MESSAGE = 1;
    public static final byte BLOCK = 2;
    /**
     * 1 MiB. Limits frames, not files: a file is sent as many frames.
     */
    public static final int MAX_FRAME = 1024 * 1024;

    private FrameCodec() {
    }

    public static final class Frame {
        public final byte type;
        public final byte[] payload;

        public Frame(byte type, byte[] payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    /**
     * The partner broke the rules of the wire format.
     */
    public static class ProtocolViolationException extends IOException {
        public ProtocolViolationException(String message) {
            super(message);
        }
    }

    /**
     * Writes and flushes one frame. The caller makes sure that nobody else writes at the same time.
     *
     * @throws IllegalArgumentException if the payload is longer than {@link #MAX_FRAME}. Nothing was written then.
     */
    public static void write(DataOutputStream out, byte type, byte[] payload) throws IOException {
        if (payload.length > MAX_FRAME)
            throw new IllegalArgumentException("frame of " + payload.length + " bytes exceeds the maximum of " + MAX_FRAME);
        out.writeByte(type);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    /**
     * Blocks until a whole frame is read.
     *
     * @throws ProtocolViolationException unknown type or a length that is negative or above the maximum. Raised before the payload is read or allocated.
     * @throws java.io.EOFException       the stream ended
     */
    public static Frame read(DataInputStream in) throws IOException {
        byte type = in.readByte();
        int length = in.readInt();
        if (type != MESSAGE && type != BLOCK)
            throw new ProtocolViolationException("unknown frame type " + type);
        if (length < 0 || length > MAX_FRAME)
            throw new ProtocolViolationException("frame length " + length + " is not between 0 and " + MAX_FRAME);
        byte[] payload = new byte[length];
        in.readFully(payload);
        return new Frame(type, payload);
    }
}
