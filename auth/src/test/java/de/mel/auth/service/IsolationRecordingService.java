package de.mel.auth.service;

import de.mel.auth.socket.process.transfer.MelIsolatedProcess;

import java.io.File;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Remembers which isolated connections were handed to it.
 */
public class IsolationRecordingService extends MelTestService {
    public final CopyOnWriteArrayList<MelIsolatedProcess> established = new CopyOnWriteArrayList<>();
    public final CountDownLatch firstEstablished = new CountDownLatch(1);

    public IsolationRecordingService(MelAuthService melAuthService, File workingDirectory, String uuid) {
        super(melAuthService, workingDirectory, 1L, uuid);
    }

    @Override
    public synchronized void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess) {
        super.onIsolatedConnectionEstablished(isolatedProcess);
        established.add(isolatedProcess);
        firstEstablished.countDown();
    }
}
