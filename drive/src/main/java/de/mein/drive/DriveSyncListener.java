package de.mein.drive;

import de.mein.auth.service.MeinAuthService;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.service.MeinDriveServerService;

import java.io.File;

/**
 * Created by xor on 11/13/16.
 */
public abstract class DriveSyncListener {
    public DTestStructure testStructure = new DTestStructure();
    private int count = 0;

    public int getCount() {
        return count;
    }

    public abstract void onSyncFailed();

    public abstract void onTransfersDone();

    public static class DTestStructure {
        public MeinAuthService maServer, maClient;
        public MeinDriveClientService clientDriveService;
        public MeinDriveServerService serverDriveService;
        public File testdir1;
        public File testdir2;

        public DTestStructure setClientDriveService(MeinDriveClientService clientDriveService) {
            this.clientDriveService = clientDriveService;
            return this;
        }

        public DTestStructure setMaClient(MeinAuthService maClient) {
            this.maClient = maClient;
            return this;
        }

        public DTestStructure setMaServer(MeinAuthService maServer) {
            this.maServer = maServer;
            return this;
        }

        public DTestStructure setServerDriveService(MeinDriveServerService serverDriveService) {
            this.serverDriveService = serverDriveService;
            return this;
        }

        public DTestStructure setTestdir1(File testdir1) {
            this.testdir1 = testdir1;
            return this;
        }

        public DTestStructure setTestdir2(File testdir2) {
            this.testdir2 = testdir2;
            return this;
        }
    }

    public void onSyncDone() {
        onSyncDoneImpl();
        count++;
    }

    public abstract void onSyncDoneImpl();
}

