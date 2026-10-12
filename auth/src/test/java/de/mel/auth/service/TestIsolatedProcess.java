package de.mel.auth.service;

import de.mel.auth.jobs.BlockReceivedJob;
import de.mel.auth.socket.MelAuthSocket;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;

/**
 * Minimal isolated process for tests. Must be a top level class: the server finds it by class name.
 */
public class TestIsolatedProcess extends MelIsolatedProcess {
    public TestIsolatedProcess(MelAuthSocket melAuthSocket, IMelService melService, Long partnerCertificateId, String partnerServiceUuid, String isolatedUuid) {
        super(melAuthSocket, melService, partnerCertificateId, partnerServiceUuid, isolatedUuid);
    }

    @Override
    public void onBlockReceived(BlockReceivedJob bytes) {
    }
}
