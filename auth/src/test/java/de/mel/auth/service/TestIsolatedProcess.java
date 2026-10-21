package de.mel.auth.service;

import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal isolated process for tests: remembers the blocks it gets and sends blocks on request.
 * Must be a top level class: the service looks it up by class name.
 */
public class TestIsolatedProcess extends MelIsolatedProcess {
    public final List<byte[]> received = new CopyOnWriteArrayList<>();

    public TestIsolatedProcess(MelAuthSocket melAuthSocket, IMelService melService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(melAuthSocket, melService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
    }

    @Override
    public void onBlockReceived(BlockReceivedJob bytes) {
        received.add(bytes.getBlock());
    }

    public void sendBlock(byte[] block) {
        melAuthSocket.sendBlock(block);
    }
}
