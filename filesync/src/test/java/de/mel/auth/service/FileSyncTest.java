package de.mel.auth.service;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.file.IFile;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.N;
import de.mel.auth.tools.ShutDownDeferredManager;
import de.mel.filesync.FileSyncBootloader;
import de.mel.filesync.FileSyncCreateServiceHelper;
import de.mel.filesync.FileSyncSyncListener;
import de.mel.filesync.bash.BashTools;
import de.mel.filesync.serialization.TestDirCreator;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.sql.RWLock;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

public class FileSyncTest {
    public static MelAuthService maServiceServer;
    public static MelAuthService maServiceClient;

    public static MelFileSyncClientService fsServiceClient;
    public static MelFileSyncServerService fsServiceServer;
    public static RWLock lock = new RWLock();
    public static N runner = new N(Throwable::printStackTrace);
    public static MelAuthSettings jsonClient = createJsonClient();
    public static MelAuthSettings jsonServer = createJsonServer();
    public static IFile testdirServer;
    public static IFile testdirClient;

    public static MelAuthSettings createJsonClient() {
        MelAuthSettings settings = new MelAuthSettings().setPort(8890).setDeliveryPort(8891)
                .setBrotcastPort(9966) // does not listen! only one listener seems possible
                .setBrotcastListenerPort(6699).setBrotcastPort(9966)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir2()).setName("MAClient").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MelBoot.Companion.getDefaultWorkingDir2(), MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
    }

    public static MelAuthSettings createJsonServer() {
        MelAuthSettings settings = new MelAuthSettings().setPort(8888).setDeliveryPort(8889)
                .setBrotcastListenerPort(9966).setBrotcastPort(6699)
                .setWorkingDirectory(MelBoot.Companion.getDefaultWorkingDir1()).setName("MAServer").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(MelBoot.Companion.getDefaultWorkingDir1(), MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        return settings;
    }

    @After
    public void after() throws IOException {
        CountLock shutdownLock = new CountLock().lock();
        ShutDownDeferredManager shut = new ShutDownDeferredManager();
        N.r(() -> shut.when(maServiceServer.shutDown()));
        N.r(() -> shut.when(maServiceClient.shutDown()));
        shut.digest().done(result -> shutdownLock.unlock());
        shutdownLock.lock();
        maServiceServer = maServiceClient = null;
        lock = null;
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir1());
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir2());
        BashTools.Companion.rmRf(testdirServer);
        BashTools.Companion.rmRf(testdirClient);
    }

    @Before
    public void before() throws IOException {
        AbstractFile.configure(new DefaultFileConfiguration());
        BashTools.Companion.init();
        lock = new RWLock();
        testdirServer = AbstractFile.instance("testdir1");
        testdirClient = AbstractFile.instance("testdir2");
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir1());
        BashTools.Companion.rmRf(MelBoot.Companion.getDefaultWorkingDir2());
        BashTools.Companion.rmRf(testdirServer);
        BashTools.Companion.rmRf(testdirClient);
    }

    public void conflictImpl(MelBoot bootClient) throws Exception {
        final FileSyncSyncListener clientSyncListener = new FileSyncSyncListener() {
            @Override
            public void onSyncFailed() {
                Lok.debug("dzz sync fail");
            }

            @Override
            public void onTransfersDone() {
                Lok.debug("dzz transfer done");
            }

            @Override
            public void onSyncDoneImpl() {
                Lok.debug("dzz sync done");
            }
        };
        setupConflict(clientSyncListener, bootClient);

    }

    /**
     * Sets up a client/server Mel instance and connects them. Creates a {@link de.mel.filesync.service.MelFileSyncService} on each side and grants them access to each other.
     *
     * @param clientSyncListener
     * @param nullableBootClient
     * @throws Exception
     */
    private void setupConflict(FileSyncSyncListener clientSyncListener, MelBoot nullableBootClient) throws Exception {
        // dir stuff
        TestDirCreator.createTestDir(FileSyncTest.testdirServer, 1);
        TestDirCreator.createTestDir(FileSyncTest.testdirServer);
        TestDirCreator.createTestDir(FileSyncTest.testdirClient, "from client");

        // create MelBoot if null
        MelBoot bootServer = new MelBoot(jsonServer, new PowerManager(jsonServer), FileSyncBootloader.class);
        if (nullableBootClient == null)
            nullableBootClient = new MelBoot(jsonClient, new PowerManager(jsonClient), FileSyncBootloader.class);
        final MelBoot bootClient = nullableBootClient;

        // boot server
        bootServer.boot().done(maServiceServer -> {
            N.r(() -> {
                // make server accept everything
                Lok.debug("setupConflict() server booted");
                FileSyncTest.maServiceServer = maServiceServer;
                FileSyncTest.maServiceServer.addRegisterHandler(FileSyncTestBoilerplate.registerHandlerAlwaysAccepts);
                FileSyncTest.maServiceServer.addRegisteredHandler(FileSyncTestBoilerplate.registeredHandlerAlwaysGrantsAllAccess);
                // set callback when server is done indexing its root directory
                FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = fsServiceServer -> N.r(() -> {
                    FileSyncTest.fsServiceServer = (MelFileSyncServerService) fsServiceServer;
                    // boot client
                    bootClient.boot().done(maServiceClient -> N.r(() -> {
                        // make client accept everything
                        FileSyncTest.maServiceClient = maServiceClient;
                        FileSyncTest.maServiceClient.addRegisterHandler(FileSyncTestBoilerplate.registerHandlerAlwaysAccepts);
                        FileSyncTest.maServiceClient.addRegisteredHandler((melAuthService, registered) -> N.r(() -> {
                            Lok.debug("setupConflict() client registered");
                            // start communication. this causes a sync with some conflicts
                            FileSyncTest.maServiceClient.connect("localhost", FileSyncTest.maServiceServer.getSettings().getPort(),
                                            FileSyncTest.maServiceServer.getSettings().getDeliveryPort(),
                                            false)
                                    .done(mvp -> new Thread(() -> N.r(() -> {
                                        FileSyncBootloader.DEV_DRIVE_BOOT_LISTENER = fsServiceClient -> N.r(() -> {
                                            FileSyncTest.fsServiceClient = (MelFileSyncClientService) fsServiceClient;
                                            // setup test structure that allows analysis of what happened
                                            clientSyncListener.testStructure.setMaClient(FileSyncTest.maServiceClient)
                                                    .setMaServer(FileSyncTest.maServiceServer)
                                                    .setClientDriveService((MelFileSyncClientService) fsServiceClient)
                                                    .setServerDriveService((MelFileSyncServerService) fsServiceServer)
                                                    .setTestdirServer(FileSyncTest.testdirServer)
                                                    .setTestdirClient(FileSyncTest.testdirClient);
                                            FileSyncTest.fsServiceClient.setSyncListener(clientSyncListener);
                                        });
                                        // this causes communication and must not be called from a communication thread to avoid a deadlock.
                                        new FileSyncCreateServiceHelper(FileSyncTest.maServiceClient).createClientService("client service", FileSyncTest.testdirClient, 1l, FileSyncTest.fsServiceServer.getUuid(), 0.01f, 30, false);
                                    })).start());
                        }));
                        // introduce the MelAuth instances to each other
                        N.r(() -> FileSyncTest.maServiceClient.connect("localhost", 8888, 8889, true));
                    }));
                });
                new FileSyncCreateServiceHelper(FileSyncTest.maServiceServer).createServerService("server service", FileSyncTest.testdirServer, 0.01f, 30, false);
            });
        });
    }
}
