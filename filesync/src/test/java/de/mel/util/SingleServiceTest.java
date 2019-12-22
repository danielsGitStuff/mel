package de.mel.util;

import de.mel.Lok;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.service.DummyMelAuthService;
import de.mel.auth.service.MelAuthService;
import de.mel.auth.tools.N;
import de.mel.filesync.data.FileSyncSettings;
import de.mel.filesync.data.FileSyncStrings;
import de.mel.filesync.data.fs.RootDirectory;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncClientService;
import de.mel.filesync.service.MelFileSyncServerService;
import de.mel.filesync.service.Wastebin;
import de.mel.filesync.service.sync.ClientSyncHandler;
import de.mel.filesync.sql.FileSyncDatabaseManager;
import de.mel.filesync.sql.Stage;
import de.mel.filesync.sql.StageSet;
import de.mel.sql.SqlQueriesException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class SingleServiceTest extends MergeTest {

    MelFileSyncClientService syncClientService;
    FileSyncSettings settings;
    RootDirectory rootDir;
    MelAuthService melAuthService;
    private static int counter = 0;

    @Before
    public void before() throws SqlQueriesException, IOException, SQLException, InterruptedException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        super.before();
        MelAuthSettings authSettings = new MelAuthSettings()
                .setName("mel auth dummy")
                .setWorkingDirectory(workingDirectory);
        melAuthService = new DummyMelAuthService(authSettings);

        rootDir = new RootDirectory();
        rootDir.setOriginalFile(AbstractFile.instance(AbstractFile.instance(workingDirectory), "rootdir"));

        this.settings = new FileSyncSettings()
                .setFastBoot(true)
                .setLastSyncedVersion(0L)
                .setMaxAge(30L)
                .setRole(FileSyncStrings.ROLE_CLIENT)
                .setMaxWastebinSize(9999999L)
                .setRootDirectory(rootDir)
                .setUseSymLinks(false)
                .setTransferDirectory(AbstractFile.instance(AbstractFile.instance(workingDirectory), "transfer.dir"));

        syncClientService = new MelFileSyncClientService(melAuthService, workingDirectory, 1L, "dummy service uuid", this.settings, databaseManager);
        setProperty(syncClientService, "wastebin", new Wastebin(syncClientService));
        setProperty(syncClientService, "syncHandler", ClientSyncHandler.testIntance(melAuthService, syncClientService));

//        getMethod(syncClientService.getClass(), "initSyncHandler").invoke(syncClientService);
        counter++;
    }

    private void setProperty(Object object, String property, Object value) throws NoSuchFieldException, IllegalAccessException {
        Field field = null;
        Class clazz = object.getClass();
        while (field == null) {
            try {
                field = clazz.getDeclaredField(property);
            } catch (Exception e) {
            }
            clazz = clazz.getSuperclass();
        }
        field.setAccessible(true);
        field.set(object, value);
    }


    public Method getMethod(Class clazz, String methodName) throws NoSuchMethodException {
        System.err.println(clazz);
        Method[] methods = clazz.getDeclaredMethods();
        N.forEach(methods, method -> Lok.debug(method.getName() + " from " + method.getDeclaringClass().getSimpleName()));
        Method method = N.first(methods, m -> m.getName().equals(methodName));
        method.setAccessible(true);
        return method;
    }

    @Test
    public void commitJob() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, SqlQueriesException {
        makeStageSetsFromFs();
        Method workWorkWork = getMethod(syncClientService.getClass(), "workWorkWork");
        workWorkWork.invoke(syncClientService, new CommitJob());
        fail("note complete yet");
    }

    private void makeStageSetsFromFs() throws SqlQueriesException {
        localStageSet.setSource(FileSyncStrings.STAGESET_SOURCE_FS)
                .setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
        remoteStageSet.setSource(FileSyncStrings.STAGESET_SOURCE_FS)
                .setStatus(FileSyncStrings.STAGESET_STATUS_STAGED);
        stageDao.updateStageSet(localStageSet);
        stageDao.updateStageSet(remoteStageSet);
    }

    @Test
    public void mergeFsLocalEqual() throws SqlQueriesException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        makeStageSetsFromFs();
        Method method = getMethod(syncClientService.getClass(), "workWorkWork");
        method.invoke(syncClientService, new CommitJob());

        // check if all stages of the local stageset live in the merged one
        StageSet mergedStageSet = stageDao.getStagedStageSetsFromFS().stream().findFirst().get();
        Map<String, Stage> localNameMap = new HashMap<>();
        creationLocalDao.getEntries().forEach(stage -> localNameMap.put(stage.getName(), stage));
        stageDao.getStagesByStageSet(mergedStageSet.getId().v()).toList().forEach(stage -> {
            assertNotNull(localNameMap.remove(stage.getName()));
        });
        assertEquals(0, localNameMap.size());
    }

    @After
    @Override
    public void after() throws SqlQueriesException {
        super.after();
        workingDirectory.deleteOnExit();
    }
}
