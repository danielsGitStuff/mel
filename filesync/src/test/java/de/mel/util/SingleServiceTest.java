package de.mel.util;

import de.mel.auth.file.AbstractFile;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.sql.SqlQueriesException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

public class SingleServiceTest extends MergeTest {

    File workingDirectory;
    FileSyncDatabaseManager databaseManager;
    MelFileSyncServerService syncServerService;
    FileSyncSettings settings;
    RootDirectory rootDir;

    @Before
    @Override
    public void before() throws SqlQueriesException, IOException, SQLException {
        super.before();
        workingDirectory = new File("test.workingdir." + counter);
        rootDir = new RootDirectory();
        rootDir.setOriginalFile(AbstractFile.instance(AbstractFile.instance(workingDirectory), "rootdir"));
        settings = new FileSyncSettings()
                .setFastBoot(true)
                .setLastSyncedVersion(0L)
                .setMaxAge(30L)
                .setRole(FileSyncStrings.ROLE_SERVER)
                .setMaxWastebinSize(9999999L)
                .setRootDirectory(rootDir)
                .setUseSymLinks(false)
                .setTransferDirectory(AbstractFile.instance(AbstractFile.instance(workingDirectory), "transfer.dir"));

        databaseManager = new FileSyncDatabaseManager("dummy service uuid", workingDirectory, settings);
    }

    public MelFileSyncServerService createServerService() {
        syncServerService = new MelFileSyncServerService(null, workingDirectory, 1L, "dummy service uuid", settings, databaseManager);
        return syncServerService;
    }

    public Method getMethod(Object object, String methodName) throws NoSuchMethodException {
        Class<?> clazz = object.getClass();
        System.err.println(clazz);
        Method[] methods = clazz.getDeclaredMethods();
        Method method = N.first(methods, m -> m.getName().contains(methodName));
        method.setAccessible(true);
        return method;
    }

    @Test
    public void commitJob() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        createServerService();
        Method workWorkWork = getMethod(syncServerService, "workWorkWork");
        workWorkWork.invoke(syncServerService, new CommitJob());
    }

    @After
    @Override
    public void after() throws SqlQueriesException {
        super.after();
        workingDirectory.deleteOnExit();
    }
}
