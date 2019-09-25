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
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.MelSocket;
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
    public void onSocketClosed(int code, String reason, boolean remote) {
        super.onSocketClosed(code, reason, remote);
    }

    @Override
    public void onBlockReceived(BlockReceivedJob blockJob) {
        byte[] bytes = blockJob.getBlock();
        assert bytes.length == MelSocket.BLOCK_SIZE;
        byte[] streamBytes = Arrays.copyOfRange(bytes, 1, 5);
        int streamId = ByteTools.bytesToInt(streamBytes);
        Block block = new Block(streamId).setBytes(bytes);
        try {
            while (block.getFirstByteToProcessIndex() < block.getBytes().length && !(block.getBytes()[block.getFirstByteToProcessIndex()] == 0)) {
                if (!handleTransfer(block)) {
                    Lok.error("MelIsolatedFileProcess.onBlockReceived. did not know what to do :(");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            blockJob.resolve(null);
        }
    }


    private static final int META_LENGTH = 17;

    /**
     * //[8 bytes, file size]<br>
     * format first block: [T][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
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
     * sends one block of bytes over the socket. so it does not block until the file is transferred.<br>
     * format first/standard block: [T][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
     * format last block: [t][4 bytes, stream id][8bytes, offset of PAYLOAD][8 bytes, length of PAYLOAD][PAYLOAD]
     *
     * @throws IOException
     */
    public void transfer() throws IOException, InterruptedException {
        int blockOffset = 0;
        int bytesLeft = MelSocket.BLOCK_SIZE;
        byte[] block = new byte[MelSocket.BLOCK_SIZE];
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
            // [T/t][streamId][offset]
            Character charToSend = transferDetail.hasError() ? 'E' : 'T';
            if (!transferDetail.hasError()) {
                // already done, already done or it fits completely
                if (transferDetail.transferred() || transferDetail.getPosition() == transferDetail.getEnd()
                        || transferDetail.getEnd() - transferDetail.getPosition() <= bytesLeft - META_LENGTH)
                    charToSend = 't';
                blockOffset = ByteTools.fill(block, blockOffset,
                        charToSend.toString().getBytes(),
                        ByteTools.intToBytes(transferDetail.getStreamId()),
                        ByteTools.longToBytes(transferDetail.getPosition())
                );
            } else {
                blockOffset = ByteTools.fill(block, blockOffset,
                        charToSend.toString().getBytes(),
                        ByteTools.intToBytes(transferDetail.getStreamId()));
            }
            FileTransferDetail.FReadInfo readResult = null;
            if (!transferDetail.hasError()) {
                //keep in mind that 4 bytes still have to be added before the actual PAYLOAD
                bytesLeft = MelSocket.BLOCK_SIZE - blockOffset - 4;
                readResult = transferDetail.readFile(transferDetail.getPosition(), bytesLeft);
                // [length of PAYLOAD]
                blockOffset = ByteTools.fill(block, blockOffset, ByteTools.intToBytes(readResult.getBytes().length), readResult.getBytes());
            }
            bytesLeft = MelSocket.BLOCK_SIZE - blockOffset;
            // if file is processed
            if (transferDetail.transferred() || transferDetail.hasError() || readResult == null || (readResult != null && readResult.getNotFilledBytes() > 0)) {
                sendingSemaphore.acquire();
                sendingDetails.poll();
                hashFTDSendingMap.remove(hash);
                sendingSemaphore.release();
            }
            if (bytesLeft > 0) {
                if (bytesLeft > META_LENGTH) {
                    sendingSemaphore.acquire();
                    hash = sendingDetails.peek();
                    transferDetail = hashFTDSendingMap.get(hash);
                    sendingSemaphore.release();
                    if (transferDetail == null)
                        melAuthSocket.sendBlock(block);
                } else {
                    transferDetail = null;
                    melAuthSocket.sendBlock(block);
                }
            } else {
                melAuthSocket.sendBlock(block);
                transferDetail = null;
            }
        }
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
