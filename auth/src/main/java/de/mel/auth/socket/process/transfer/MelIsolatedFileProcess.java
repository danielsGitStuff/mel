package de.mel.auth.socket.process.transfer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;

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
import de.mel.sql.RWLock;

/**
 * Transfers files.<br>
 * see {@link MelIsolatedProcess}
 */
public class MelIsolatedFileProcess extends MelIsolatedProcess implements MelRunnable {
    private Map<String, FileTransferDetail> hashFTDSendingMap = new HashMap<>();
    private Map<String, FileTransferDetail> hashFTDReceivingMap = new HashMap<>();

    private Queue<String> sendingDetails = new LinkedList();
    private Map<Integer, FileTransferDetail> streamIdFileMapReceiving = new TreeMap<>();

    private Semaphore sendingSemaphore = new Semaphore(1, true);
    private Semaphore receivingSemaphore = new Semaphore(1, true);
    private RWLock sendWaitLock = new RWLock();
    private MelNotification sendingNotification;

    public void cancelByHash(String hash) throws InterruptedException {
        try {
            sendingSemaphore.acquire();
            sendingDetails.remove(hash);
            FileTransferDetail detail = hashFTDSendingMap.remove(hash);
            if (detail != null) {
            }
        } finally {
            sendingSemaphore.release();
        }
        try {
            receivingSemaphore.acquire();
            FileTransferDetail detail = hashFTDReceivingMap.remove(hash);
            if (detail != null)
                streamIdFileMapReceiving.remove(detail.getStreamId());
        } finally {
            receivingSemaphore.release();
        }

    }


    public void addFilesReceiving(Collection<FileTransferDetail> fileTransferDetails) throws InterruptedException {
        receivingSemaphore.acquire();
        for (FileTransferDetail fileTransferDetail : fileTransferDetails) {
            hashFTDReceivingMap.put(fileTransferDetail.getHash(), fileTransferDetail);
            streamIdFileMapReceiving.put(fileTransferDetail.getStreamId(), fileTransferDetail);
        }
        receivingSemaphore.release();
    }

    public void addFilesReceiving(FileTransferDetail fileTransferDetail) throws InterruptedException {
//        Lok.debug("MelIsolatedFileProcess.addFilesReceiving.ID: " + fileTransferDetail.getStreamId());
        receivingSemaphore.acquire();
        hashFTDReceivingMap.put(fileTransferDetail.getHash(), fileTransferDetail);
        streamIdFileMapReceiving.put(fileTransferDetail.getStreamId(), fileTransferDetail);
//        Lok.debug("MelIsolatedFileProcess.addFilesReceiving.stored.hash: " + streamIdFileMapReceiving.get(fileTransferDetail.getStreamId()).getHash());
        receivingSemaphore.release();
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
        super.onSocketClosed(reason, remote);
    }

    /**
     * One block frame is one record, see {@link #transfer()}.
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
            byte[] data = Arrays.copyOfRange(bytes, firstByteToProcess + META_LENGTH, firstByteToProcess + META_LENGTH + length);
            assert length == data.length;
            FileTransferDetail transferDetail = streamIdFileMapReceiving.get(streamId);
            if (transferDetail == null) {
                Lok.debug("MelIsolatedFileProcess.handleTransfer.NULL, id was: " + block.getStreamId());
                block.setFirstByteToProcessIndex(block.getBytes().length);
                return true;
            }
            transferDetail.onReceived(offset, data);
            if (transferDetail.transferred() || c.equals('t')) {
                receivingSemaphore.acquire();
                Lok.debug("MelIsolatedFileProcess.handleTransfer.remove.receiving.id: " + transferDetail.getStreamId());
                streamIdFileMapReceiving.remove(streamId);
                receivingSemaphore.release();
            }
            block.setFirstByteToProcessIndex(firstByteToProcess + length + META_LENGTH);
            return true;
        } else if (c.equals('E')) {
            byte[] bytes = block.getBytes();
            int firstByteToProcess = block.getFirstByteToProcessIndex();
            int streamId = ByteTools.bytesToInt(bytes, firstByteToProcess + 1);
            FileTransferDetail transferDetail = streamIdFileMapReceiving.get(streamId);
            if (transferDetail == null) {
                Lok.debug("MelIsolatedFileProcess.handleTransfer.NULL, id was: " + block.getStreamId());
            } else {
                transferDetail.setError(true);
                transferDetail.onFailed();
            }
            block.setFirstByteToProcessIndex(firstByteToProcess + 5);
            streamIdFileMapReceiving.remove(streamId);
            return true;
        }
        return false;
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

    public void sendFile(FileTransferDetail transferDetail) throws IOException, JsonSerializationException, IllegalAccessException, InterruptedException {
        sendingSemaphore.acquire();
        sendingDetails.add(transferDetail.getHash());
        hashFTDSendingMap.put(transferDetail.getHash(), transferDetail);
        sendingSemaphore.release();
        sendWaitLock.unlockWrite();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                sendingSemaphore.acquire();
                String hash = sendingDetails.peek();
                FileTransferDetail details = hashFTDSendingMap.get(hash);
                sendingSemaphore.release();
                if (details != null && !Thread.currentThread().isInterrupted()) {
                    transfer();
                } else {
                    // wait
                    sendWaitLock.lockWrite();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        stop();
    }

    /**
     * Sends the files that are waiting, each piece in a block frame of its own (the frame has exactly the size it needs). So it does not block until the file is transferred.<br>
     * format of a record: [T][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
     * the last record of a file starts with [t] instead of [T]<br>
     * a file that cannot be sent: [E][4 bytes, stream id]
     *
     * @throws IOException
     */
    public void transfer() throws IOException, InterruptedException {
        String hash = sendingDetails.peek();
        FileTransferDetail transferDetail = hashFTDSendingMap.get(hash);
        while (transferDetail != null) {
            //notification first
            if (sendingNotification == null) {
                sendingNotification = service.createSendingNotification();
                if (sendingNotification != null) {
                    melAuthSocket.getMelAuthService().onNotificationFromService(service, sendingNotification);
                }
            }
            byte[] record;
            FileTransferDetail.FReadInfo readResult = null;
            if (transferDetail.hasError()) {
                record = new byte[5];
                ByteTools.fill(record, 0, "E".getBytes(), ByteTools.intToBytes(transferDetail.getStreamId()));
            } else {
                boolean last = transferDetail.transferred() || transferDetail.getPosition() == transferDetail.getEnd()
                        || transferDetail.getEnd() - transferDetail.getPosition() <= MAX_DATA;
                long position = transferDetail.getPosition();
                readResult = transferDetail.readFile(position, MAX_DATA);
                byte[] data = readResult.getBytes();
                record = new byte[META_LENGTH + data.length];
                ByteTools.fill(record, 0,
                        (last ? "t" : "T").getBytes(),
                        ByteTools.intToBytes(transferDetail.getStreamId()),
                        ByteTools.longToBytes(position),
                        ByteTools.intToBytes(data.length),
                        data);
            }
            // if file is processed
            if (transferDetail.transferred() || transferDetail.hasError() || readResult == null || readResult.getNotFilledBytes() > 0) {
                sendingSemaphore.acquire();
                sendingDetails.poll();
                hashFTDSendingMap.remove(hash);
                sendingSemaphore.release();
            }
            melAuthSocket.sendBlock(record);
            sendingSemaphore.acquire();
            hash = sendingDetails.peek();
            transferDetail = hashFTDSendingMap.get(hash);
            sendingSemaphore.release();
        }
        if (sendingNotification != null)
            sendingNotification.cancel();
        sendingNotification = null;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    public void sendError(FileTransferDetail detail) throws InterruptedException {
        detail.setError(true);
        sendingSemaphore.acquire();
        hashFTDSendingMap.put(detail.getHash(), detail);
        sendingDetails.add(detail.getHash());
        sendingSemaphore.release();
        sendWaitLock.unlockWrite();
    }
}
