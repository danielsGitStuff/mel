package de.mel.filesync;

import de.mel.auth.file.IFile;
import de.mel.auth.service.MelAuthServiceImpl;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;

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
        public MelAuthServiceImpl maServer, maClient;
        public MelFileSyncClientService clientDriveService;
        public MelFileSyncServerService serverDriveService;
        public IFile testdir1;
        public IFile testdir2;

        public DTestStructure setClientDriveService(MelFileSyncClientService clientDriveService) {
            this.clientDriveService = clientDriveService;
            return this;
        }

        public DTestStructure setMaClient(MelAuthServiceImpl maClient) {
            this.maClient = maClient;
            return this;
        }

        public DTestStructure setMaServer(MelAuthServiceImpl maServer) {
            this.maServer = maServer;
            return this;
        }

        public DTestStructure setServerDriveService(MelFileSyncServerService serverDriveService) {
            this.serverDriveService = serverDriveService;
            return this;
        }

        public DTestStructure setTestdir1(IFile testdir1) {
            this.testdir1 = testdir1;
            return this;
        }

        public DTestStructure setTestdir2(IFile testdir2) {
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

