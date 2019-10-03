package de.mel.drive;

import de.mel.auth.file.AFile;
import de.mel.auth.service.MelAuthService;
import de.mel.drive.service.MelFileSyncClientService;
import de.mel.drive.service.MelFileSyncServerService;

/**
 * Created by xor on 11/13/16.
 */
public abstract class FileSyncSyncListener {
    public DTestStructure testStructure = new DTestStructure();
    private int count = 0;

    public int getCount() {
        return count;
    }

    public abstract void onSyncFailed();

    public abstract void onTransfersDone();


    public static class DTestStructure {
        public MelAuthService maServer, maClient;
        public MelFileSyncClientService clientDriveService;
        public MelFileSyncServerService serverDriveService;
        public AFile testdir1;
        public AFile testdir2;

        public DTestStructure setClientDriveService(MelFileSyncClientService clientDriveService) {
            this.clientDriveService = clientDriveService;
            return this;
        }

        public DTestStructure setMaClient(MelAuthService maClient) {
            this.maClient = maClient;
            return this;
        }

        public DTestStructure setMaServer(MelAuthService maServer) {
            this.maServer = maServer;
            return this;
        }

        public DTestStructure setServerDriveService(MelFileSyncServerService serverDriveService) {
            this.serverDriveService = serverDriveService;
            return this;
        }

        public DTestStructure setTestdir1(AFile testdir1) {
            this.testdir1 = testdir1;
            return this;
        }

        public DTestStructure setTestdir2(AFile testdir2) {
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

