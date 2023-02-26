package de.mel.auth.service;

import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.Eva;
import de.mel.auth.tools.WaitLock;
import de.mel.auth.tools.lock2.P;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.boot.FileSyncFXBootloader;
import org.junit.Test;

public class FxTest2 {

    @Test
    public void conflict() throws Exception {
        Eva.enable();
        P.enableDebugPrinting();
        FileSyncTest fileSyncTest = new FileSyncTest();
        try {
            fileSyncTest.before();
            MelBoot bootClient = new MelBoot(FileSyncTest.jsonClient, new PowerManager(FileSyncTest.jsonClient), FileSyncFXBootloader.class).addMelAuthAdmin(new MelAuthFxLoader());
            fileSyncTest.conflictImpl(bootClient);
            new WaitLock().lock().lock();
        } finally {
            fileSyncTest.after();
        }
    }

    @Test
    public void conflictWithAndroid() throws Exception {
        Eva.enable();
        P.enableDebugPrinting();
        FileSyncTest fileSyncTest = new FileSyncTest()
                .setServerBootloaderClass(FileSyncBootloader.class)
                .setServerMelBootConfig(melBoot -> {
                    melBoot.addMelAuthAdmin(new MelAuthFxLoader());
                });
        try {
            fileSyncTest.before();
            fileSyncTest.conflictImpl(null);
            new WaitLock().lock().lock();
        } finally {
            fileSyncTest.after();
        }
    }
}

