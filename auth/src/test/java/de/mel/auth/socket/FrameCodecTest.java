package de.mel.auth.socket;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class FrameCodecTest {
    private static byte[] encode(byte type, byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FrameCodec.write(new DataOutputStream(bytes), type, payload);
        return bytes.toByteArray();
    }

    private static FrameCodec.Frame decode(byte[] bytes) throws IOException {
        return FrameCodec.read(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    private static byte[] header(byte type, int length) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(type);
        out.writeInt(length);
        return bytes.toByteArray();
    }

    @Test
    public void roundTrip() throws Exception {
        byte[] payload = "hello ä中".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] wire = encode(FrameCodec.MESSAGE, payload);
        assertEquals(5 + payload.length, wire.length);
        assertEquals(FrameCodec.MESSAGE, wire[0]);
        FrameCodec.Frame frame = decode(wire);
        assertEquals(FrameCodec.MESSAGE, frame.type);
        assertArrayEquals(payload, frame.payload);
    }

    @Test
    public void blocksAreFramesToo() throws Exception {
        FrameCodec.Frame frame = decode(encode(FrameCodec.BLOCK, new byte[]{1, 2, 3}));
        assertEquals(FrameCodec.BLOCK, frame.type);
        assertArrayEquals(new byte[]{1, 2, 3}, frame.payload);
    }

    @Test
    public void emptyPayload() throws Exception {
        FrameCodec.Frame frame = decode(encode(FrameCodec.MESSAGE, new byte[0]));
        assertEquals(0, frame.payload.length);
    }

    @Test
    public void exactlyTheMaximumIsFine() throws Exception {
        byte[] payload = new byte[FrameCodec.MAX_FRAME];
        payload[payload.length - 1] = 7;
        FrameCodec.Frame frame = decode(encode(FrameCodec.BLOCK, payload));
        assertEquals(FrameCodec.MAX_FRAME, frame.payload.length);
        assertEquals(7, frame.payload[frame.payload.length - 1]);
    }

    @Test
    public void oneByteOverTheMaximumIsRefusedByTheWriter() {
        assertThrows(IllegalArgumentException.class, () -> encode(FrameCodec.BLOCK, new byte[FrameCodec.MAX_FRAME + 1]));
    }

    @Test
    public void aHeaderAnnouncingMoreThanTheMaximumIsAViolationBeforeAnythingIsAllocated() {
        // the stream has no payload at all: a reader that allocated or read first would fail differently
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header(FrameCodec.MESSAGE, FrameCodec.MAX_FRAME + 1)));
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header(FrameCodec.MESSAGE, Integer.MAX_VALUE)));
    }

    @Test
    public void aNegativeLengthIsAViolation() {
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header(FrameCodec.MESSAGE, -1)));
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header(FrameCodec.MESSAGE, Integer.MIN_VALUE)));
    }

    @Test
    public void anUnknownTypeIsAViolation() {
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header((byte) 99, 0)));
        assertThrows(FrameCodec.ProtocolViolationException.class, () -> decode(header((byte) 0, 0)));
    }

    @Test
    public void aTruncatedPayloadIsAnEndOfStream() throws Exception {
        byte[] wire = encode(FrameCodec.MESSAGE, new byte[100]);
        byte[] cut = java.util.Arrays.copyOf(wire, wire.length - 1);
        assertThrows(EOFException.class, () -> decode(cut));
    }

    @Test
    public void aTruncatedHeaderIsAnEndOfStream() {
        assertThrows(EOFException.class, () -> decode(new byte[]{1, 0}));
        assertThrows(EOFException.class, () -> decode(new byte[0]));
    }

    @Test
    public void severalFramesInARow() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        FrameCodec.write(out, FrameCodec.MESSAGE, new byte[]{1});
        FrameCodec.write(out, FrameCodec.BLOCK, new byte[]{2, 2});
        FrameCodec.write(out, FrameCodec.MESSAGE, new byte[]{3, 3, 3});
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, FrameCodec.read(in).payload.length);
        assertEquals(FrameCodec.BLOCK, FrameCodec.read(in).type);
        assertEquals(3, FrameCodec.read(in).payload.length);
        assertThrows(EOFException.class, () -> FrameCodec.read(in));
    }
}
