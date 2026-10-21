package de.mel.auth.socket.process.transfer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import de.mel.Lok;
import de.mel.MelRunnable;
import de.mel.auth.MelNotification;
import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.service.IMelService;
import de.mel.auth.socket.CloseReason;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.FrameCodec;
import de.mel.auth.tools.ByteTools;
import de.mel.core.serialize.exceptions.JsonSerializationException;

/**
 * Transfers files.<br>
 * see {@link MelIsolatedProcess}
 */
public class MelIsolatedFileProcess extends MelIsolatedProcess implements MelRunnable {
    private Map<String, FileTransferDetail> hashFTDReceivingMap = new ConcurrentHashMap<>();
    private Map<Integer, FileTransferDetail> streamIdFileMapReceiving = new ConcurrentHashMap<>();

    /**
     * The files that are being sent. The sender takes the first one, sends a piece of it and puts it at the end, so all of them make progress
     * and a huge file does not keep the small ones waiting.
     */
    private final LinkedBlockingDeque<FileTransferDetail> sendingQueue = new LinkedBlockingDeque<>();
    private MelNotification sendingNotification;
    private final AtomicLong sentFileBytes = new AtomicLong();

    /**
     * @return how many bytes of file content this process has sent so far
     */
    public long getSentFileBytes() {
        return sentFileBytes.get();
    }

    public void cancelByHash(String hash) {
        sendingQueue.removeIf(sending -> {
            if (!hash.equals(sending.getHash()))
                return false;
            sending.abortRead();
            return true;
        });
        FileTransferDetail detail = hashFTDReceivingMap.remove(hash);
        if (detail != null) {
            streamIdFileMapReceiving.remove(detail.getStreamId());
            detail.abort();
        }

    }


    public void addFilesReceiving(Collection<FileTransferDetail> fileTransferDetails) {
        for (FileTransferDetail fileTransferDetail : fileTransferDetails)
            addFilesReceiving(fileTransferDetail);
    }

    public void addFilesReceiving(FileTransferDetail fileTransferDetail) {
        hashFTDReceivingMap.put(fileTransferDetail.getHash(), fileTransferDetail);
        streamIdFileMapReceiving.put(fileTransferDetail.getStreamId(), fileTransferDetail);
    }

    public MelIsolatedFileProcess(MelAuthSocket melAuthSocket, IMelService melService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(melAuthSocket, melService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
        melAuthSocket.getMelAuthService().execute(this);
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void onSocketClosed(CloseReason reason, boolean remote) {
        // what is still on its way will not arrive here. Let go of the files, what was written stays so it can be continued.
        for (FileTransferDetail detail : new ArrayList<>(streamIdFileMapReceiving.values()))
            detail.abort();
        // and nothing more can be sent
        FileTransferDetail sending;
        while ((sending = sendingQueue.poll()) != null)
            sending.abortRead();
        super.onSocketClosed(reason, remote);
    }

    /**
     * One block frame is one record, see {@link #sendPiece}.
     */
    @Override
    public void onBlockReceived(BlockReceivedJob blockJob) {
        byte[] bytes = blockJob.getBlock();
        if (bytes.length < 5) {
            Lok.error("MelIsolatedFileProcess.onBlockReceived: record of " + bytes.length + " bytes is too short");
            blockJob.resolveOk(null);
            return;
        }
        byte[] streamBytes = Arrays.copyOfRange(bytes, 1, 5);
        int streamId = ByteTools.bytesToInt(streamBytes);
        Block block = new Block(streamId).setBytes(bytes);
        try {
            if (!handleTransfer(block)) {
                Lok.error("MelIsolatedFileProcess.onBlockReceived. did not know what to do :(");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            blockJob.resolveOk(null);
        }
    }


    /**
     * [T/t][4 bytes stream id][8 bytes offset][4 bytes length]
     */
    private static final int META_LENGTH = 17;
    /**
     * the most file data one record (one block frame) carries
     */
    private static final int MAX_DATA = FrameCodec.MAX_FRAME - META_LENGTH;

    /**
     * format of a record: [T or t][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
     * [E][4 bytes, stream id] tells that the file cannot be sent<br>
     *
     * @param block
     * @return number of handled bytes
     */
    private boolean handleTransfer(Block block) throws IOException, InterruptedException {
        Character c = readCommand(block);
        if (c.equals('T') || c.equals('t')) {
            byte[] bytes = block.getBytes();
            int firstByteToProcess = block.getFirstByteToProcessIndex();
            int streamId = ByteTools.bytesToInt(bytes, firstByteToProcess + 1);
            long offset = ByteTools.bytesToLong(bytes, firstByteToProcess + 5);
            int length = ByteTools.bytesToInt(bytes, firstByteToProcess + 13);
            block.setFirstByteToProcessIndex(firstByteToProcess + length + META_LENGTH);
            if (length < 0 || firstByteToProcess + META_LENGTH + length > bytes.length) {
                Lok.error("MelIsolatedFileProcess.handleTransfer: record says " + length + " bytes but has " + (bytes.length - firstByteToProcess - META_LENGTH));
                failReceiving(streamId);
                return true;
            }
            byte[] data = Arrays.copyOfRange(bytes, firstByteToProcess + META_LENGTH, firstByteToProcess + META_LENGTH + length);
            FileTransferDetail transferDetail = streamIdFileMapReceiving.get(streamId);
            if (transferDetail == null) {
                Lok.debug("MelIsolatedFileProcess.handleTransfer.NULL, id was: " + streamId);
                return true;
            }
            try {
                transferDetail.onReceived(offset, data);
            } catch (IOException e) {
                Lok.error("MelIsolatedFileProcess.handleTransfer: transfer " + streamId + " fails: " + e.getMessage());
                failReceiving(streamId);
                return true;
            }
            if (transferDetail.transferred()) {
                streamIdFileMapReceiving.remove(streamId);
                hashFTDReceivingMap.remove(transferDetail.getHash(), transferDetail);
            } else if (c.equals('t')) {
                // the sender says that was the last piece but the file is not complete
                Lok.error("MelIsolatedFileProcess.handleTransfer: transfer " + streamId + " ended at " + transferDetail.getPosition() + " but should end at " + transferDetail.getEnd());
                failReceiving(streamId);
            }
            return true;
        } else if (c.equals('E')) {
            byte[] bytes = block.getBytes();
            int firstByteToProcess = block.getFirstByteToProcessIndex();
            int streamId = ByteTools.bytesToInt(bytes, firstByteToProcess + 1);
            block.setFirstByteToProcessIndex(firstByteToProcess + 5);
            FileTransferDetail transferDetail = streamIdFileMapReceiving.get(streamId);
            if (transferDetail == null) {
                Lok.debug("MelIsolatedFileProcess.handleTransfer.NULL, id was: " + streamId);
            } else {
                transferDetail.setError(true);
            }
            failReceiving(streamId);
            return true;
        }
        return false;
    }

    /**
     * Gives up on a file we are receiving: nothing more is expected for it, the file is closed and the one who wanted it is told.
     */
    private void failReceiving(int streamId) {
        FileTransferDetail transferDetail = streamIdFileMapReceiving.remove(streamId);
        if (transferDetail == null)
            return;
        hashFTDReceivingMap.remove(transferDetail.getHash(), transferDetail);
        transferDetail.onFailed();
    }

    /**
     * reads next command
     *
     * @param block
     * @return
     */
    private Character readCommand(Block block) {
        return (Character) (char) block.getBytes()[block.getFirstByteToProcessIndex()];
    }

    /**
     * Queues the file for sending, from {@link FileTransferDetail#getStart()} to {@link FileTransferDetail#getEnd()}. Returns right away.
     */
    public void sendFile(FileTransferDetail transferDetail) {
        sendingQueue.add(transferDetail);
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && isOpen()) {
                FileTransferDetail detail = sendingQueue.poll(200, TimeUnit.MILLISECONDS);
                if (detail != null) {
                    sendPiece(detail);
                } else if (sendingNotification != null) {
                    // nothing left to send
                    sendingNotification.cancel();
                    sendingNotification = null;
                }
            }
        } catch (InterruptedException e) {
            // end
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (sendingNotification != null)
            sendingNotification.cancel();
        stop();
    }

    /**
     * Sends the next piece of the file in a block frame of its own (the frame has exactly the size it needs). So a file does not block the others until it is transferred.<br>
     * format of a record: [T][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
     * the last record of a file starts with [t] instead of [T]<br>
     * a file that cannot be sent: [E][4 bytes, stream id]<br>
     * If the file is not completely sent it is put at the end of the queue.
     */
    private void sendPiece(FileTransferDetail transferDetail) {
        //notification first
        if (sendingNotification == null) {
            sendingNotification = service.createSendingNotification();
            if (sendingNotification != null) {
                melAuthSocket.getMelAuthService().onNotificationFromService(service, sendingNotification);
            }
        }
        byte[] record;
        FileTransferDetail.FReadInfo readResult = null;
        long position = transferDetail.getPosition();
        if (!transferDetail.hasError()) {
            try {
                readResult = transferDetail.readFile(position, MAX_DATA);
            } catch (IOException e) {
                Lok.error("could not read " + transferDetail.getFile().getName() + ": " + e);
                transferDetail.setError(true);
            }
        }
        boolean processed;
        if (transferDetail.hasError()) {
            record = new byte[5];
            ByteTools.fill(record, 0, "E".getBytes(), ByteTools.intToBytes(transferDetail.getStreamId()));
            processed = true;
        } else {
            byte[] data = readResult.getBytes();
            // the file ends before the range we were asked for? Then this is the last we can tell
            boolean last = transferDetail.transferred() || readResult.getNotFilledBytes() > 0;
            record = new byte[META_LENGTH + data.length];
            ByteTools.fill(record, 0,
                    (last ? "t" : "T").getBytes(),
                    ByteTools.intToBytes(transferDetail.getStreamId()),
                    ByteTools.longToBytes(position),
                    ByteTools.intToBytes(data.length),
                    data);
            sentFileBytes.addAndGet(data.length);
            processed = last;
        }
        if (processed)
            transferDetail.abortRead();
        else
            sendingQueue.addLast(transferDetail);
        melAuthSocket.sendBlock(record);
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    /**
     * Tells the partner that we cannot send that file.
     */
    public void sendError(FileTransferDetail detail) {
        detail.setError(true);
        sendingQueue.add(detail);
    }
}
