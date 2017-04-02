package de.mein.auth.socket.process.transfer;

import de.mein.auth.service.IMeinService;
import de.mein.auth.socket.MeinAuthSocket;
import de.mein.auth.socket.MeinSocket;
import de.mein.auth.tools.ByteTools;
import de.mein.core.serialize.exceptions.JsonSerializationException;
import de.mein.sql.RWLock;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Semaphore;

/**
 * Created by xor on 1/5/17.
 */
public class MeinIsolatedFileProcess extends MeinIsolatedProcess implements Runnable {
    private Map<Integer, FileTransferDetail> streamIdFileMapReceiving = new TreeMap<>();
    private Queue<FileTransferDetail> sendingDetails = new LinkedList();
    private Semaphore sendingSemaphore = new Semaphore(1,true);
    private Semaphore receivingSemaphore = new Semaphore(1, true);

    public void addFilesReceiving(Collection<FileTransferDetail> fileTransferDetails) {
        fileTransferDetails.forEach(d -> streamIdFileMapReceiving.put(d.getStreamId(), d));
    }

    public void addFilesReceiving(FileTransferDetail fileTransferDetail) throws InterruptedException {
        System.out.println("MeinIsolatedFileProcess.addFilesReceiving.ID: " + fileTransferDetail.getStreamId());
        receivingSemaphore.acquire();
        streamIdFileMapReceiving.put(fileTransferDetail.getStreamId(), fileTransferDetail);
        receivingSemaphore.release();
        System.out.println("MeinIsolatedFileProcess.addFilesReceiving.stored.hash: " + streamIdFileMapReceiving.get(fileTransferDetail.getStreamId()).getHash());
    }

    public MeinIsolatedFileProcess(MeinAuthSocket meinAuthSocket, IMeinService meinService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(meinAuthSocket, meinService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
        new Thread(this).start();
    }


    @Override
    public void onBlockReceived(byte[] bytes) {
        assert bytes.length == MeinSocket.BLOCK_SIZE;
        byte[] streamBytes = Arrays.copyOfRange(bytes, 1, 5);
        int streamId = ByteTools.bytesToInt(streamBytes);
        Block block = new Block(streamId).setBytes(bytes);
        try {
            while (block.getFirstByteToProcessIndex() < block.getBytes().length && !(block.getBytes()[block.getFirstByteToProcessIndex()] == 0)) {
                if (!handleTransfer(block)) {
                    System.err.println("MeinIsolatedFileProcess.onBlockReceived. did not know what to do :(");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static final int META_LENGTH = 17;

    /**
     * //[8 bytes, file size]
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
                System.out.println("MeinIsolatedFileProcess.handleTransfer.NULL, id was: " + block.getStreamId());
            }
            transferDetail.onReceived(offset, data);
            assert c.equals('t') == transferDetail.transferred();
            if (transferDetail.transferred() || c.equals('t')) {
                receivingSemaphore.acquire();
                System.out.println("MeinIsolatedFileProcess.handleTransfer.remove.receiving.id: " + transferDetail.getStreamId());
                streamIdFileMapReceiving.remove(streamId);
                receivingSemaphore.release();
            }
            block.setFirstByteToProcessIndex(firstByteToProcess + length + META_LENGTH);
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
        sendingDetails.add(transferDetail);
        sendingSemaphore.release();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                sendingSemaphore.acquire();
                FileTransferDetail transferDetail = sendingDetails.peek();
                if (transferDetail != null) {
                    transfer();
                } else
                    sendingSemaphore.acquire();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * sends one block of bytes over the socket. so it does not block until the file is transferred.<br>
     * format first/standard block: [T][4 bytes, stream id][8 bytes, offset of PAYLOAD][4 bytes, length of PAYLOAD][PAYLOAD]<br>
     * format last block: [t][4 bytes, stream id][8bytes, offset of PAYLOAD][8 bytes, length of PAYLOAD]
     *
     * @throws JsonSerializationException
     * @throws IllegalAccessException
     * @throws IOException
     */
    public void transfer() throws JsonSerializationException, IllegalAccessException, IOException, InterruptedException {
        int blockOffset = 0;
        int bytesLeft = MeinSocket.BLOCK_SIZE;
        byte[] block = new byte[MeinSocket.BLOCK_SIZE];
        FileTransferDetail transferDetail = sendingDetails.peek();
        while (transferDetail != null) {
            // [T/t][streamId][offset]
            Character charToSend = 'T';
            // already done, already done or it fits completely
            if (transferDetail.transferred() || transferDetail.getPosition() == transferDetail.getEnd()
                    || transferDetail.getEnd() - transferDetail.getPosition() <= bytesLeft - META_LENGTH)
                charToSend = 't';
            blockOffset = ByteTools.fill(block, blockOffset,
                    charToSend.toString().getBytes(),
                    ByteTools.intToBytes(transferDetail.getStreamId()),
                    ByteTools.longToBytes(transferDetail.getPosition())
            );
            //keep in mind that 4 bytes still have to be added before the actual PAYLOAD
            bytesLeft = MeinSocket.BLOCK_SIZE - blockOffset - 4;
            FileTransferDetail.FReadInfo readResult = transferDetail.readFile(transferDetail.getPosition(), bytesLeft);
            // [length of PAYLOAD]
            blockOffset = ByteTools.fill(block, blockOffset, ByteTools.intToBytes(readResult.getBytes().length), readResult.getBytes());
            bytesLeft = MeinSocket.BLOCK_SIZE - blockOffset;
            // if file is processed
            if (transferDetail.transferred() || readResult.getNotFilledBytes() > 0) {
                sendingSemaphore.acquire();
                sendingDetails.poll();
                sendingSemaphore.release();
            }
            if (bytesLeft > 0) {
                if (bytesLeft > META_LENGTH) {
                    sendingSemaphore.acquire();
                    transferDetail = sendingDetails.peek();
                    sendingSemaphore.release();
                    if (transferDetail == null)
                        meinAuthSocket.sendBlock(block);
                } else {
                    transferDetail = null;
                    meinAuthSocket.sendBlock(block);
                }
            } else {
                meinAuthSocket.sendBlock(block);
                transferDetail = null;
            }
        }
    }

}
