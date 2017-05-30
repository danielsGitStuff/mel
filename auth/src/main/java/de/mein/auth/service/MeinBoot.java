package de.mein.auth.service;

import de.mein.DeferredRunnable;
import de.mein.MeinRunnable;
import de.mein.auth.MeinAuthAdmin;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.tools.BackgroundExecutor;
import de.mein.auth.tools.MeinDeferredManager;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boots up the MeinAuth instance and all existing services by calling the corresponding bootloaders.
 */
public class MeinBoot extends BackgroundExecutor implements MeinRunnable {
    private static Logger logger = Logger.getLogger(MeinBoot.class.getName());
    private Set<Class<? extends BootLoader>> bootloaderClasses = new HashSet<>();
    private Map<String, Class<? extends BootLoader>> bootloaderMap = new HashMap<>();
    public static final File defaultWorkingDir1 = new File("meinauth.workingdir.1");
    public static final File defaultWorkingDir2 = new File("meinauth.workingdir.2");
    private DeferredObject<MeinAuthService, Exception, Void> deferredObject;
    private MeinAuthSettings meinAuthSettings;
    private MeinAuthService meinAuthService;
    private List<MeinAuthAdmin> meinAuthAdmins = new ArrayList<>();


    public MeinBoot(MeinAuthSettings settings, Class<? extends BootLoader>... bootloaderClasses) {
        this.meinAuthSettings = settings;
        this.deferredObject = new DeferredObject<>();
        if (bootloaderClasses != null)
            this.bootloaderClasses.addAll(Arrays.asList(bootloaderClasses));
    }

    public MeinBoot addMeinAuthAdmin(MeinAuthAdmin admin) {
        meinAuthAdmins.add(admin);
        return this;
    }


    public MeinBoot addBootLoaderClass(Class<? extends BootLoader> clazz) {
        bootloaderClasses.add(clazz);
        return this;
    }

    public Map<String, Class<? extends BootLoader>> getBootloaderMap() {
        return bootloaderMap;
    }

    public Set<Class<? extends BootLoader>> getBootloaderClasses() {
        return bootloaderClasses;
    }


    public Promise<MeinAuthService, Exception, Void> boot() throws Exception {
        execute(this);
        return deferredObject;
    }

    public static void main(String[] args) {
        // MeinAuthService meinAuthService =
    }

    @Override
    public void run() {
        try {
            meinAuthService = new MeinAuthService(meinAuthSettings);
            meinAuthService.setMeinBoot(this);
            meinAuthService.addAllMeinAuthAdmin(meinAuthAdmins);
            DeferredObject<DeferredRunnable, Exception, Void> promiseAuthIsUp = meinAuthService.prepareStart();
            List<BootLoader> bootLoaders = new ArrayList<>();
            for (Class<? extends BootLoader> bootClass : bootloaderClasses) {
                logger.log(Level.FINE, "MeinBoot.boot.booting: " + bootClass.getCanonicalName());
                BootLoader bootLoader = createBootLoader(meinAuthService, bootClass);
                bootloaderMap.put(bootLoader.getName(), bootClass);
                bootLoaders.add(bootLoader);
            }
            List<Promise> bootedPromises = new ArrayList<>();
            for (BootLoader bootLoader : bootLoaders) {
                List<Service> services = meinAuthService.getDatabaseManager().getServicesByType(bootLoader.getTypeId());
                Promise<Void, Exception, Void> booted = bootLoader.boot(meinAuthService, services);
                if (booted != null)
                    bootedPromises.add(booted);
            }
            promiseAuthIsUp.done(result -> {
                deferredObject.resolve(meinAuthService);
            });
            new MeinDeferredManager().when(bootedPromises)
                    .done(nil -> meinAuthService.start()).fail(result -> {
                System.err.println("MeinBoot.run.AT LEAST ONE SERVICE FAILED TO BOOT");
            });
        } catch (Exception e) {
            e.printStackTrace();
            deferredObject.reject(e);
        }
    }

    public static BootLoader createBootLoader(MeinAuthService meinAuthService, Class<? extends BootLoader> bootClass) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        BootLoader bootLoader = bootClass.newInstance();
        bootLoader.setMeinAuthService(meinAuthService);
        DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
        MeinAuthSettings meinAuthSettings = meinAuthService.getSettings();
        ServiceType serviceType = databaseManager.getServiceTypeByName(bootLoader.getName());
        if (serviceType == null) {
            serviceType = databaseManager.createServiceType(bootLoader.getName(), bootLoader.getDescription());
        }
        bootLoader.setTypeId(serviceType.getId().v());
        File bootDir = new File(meinAuthSettings.getWorkingDirectory()
                + File.separator
                + "servicetypes"
                + File.separator
                + serviceType.getType().v());
        bootDir.mkdirs();
        bootLoader.setBootLoaderDir(bootDir);
        return bootLoader;
    }

    public BootLoader getBootLoader(String typeName) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Class<? extends BootLoader> bootClazz = bootloaderMap.get(typeName);
        BootLoader bootLoader = createBootLoader(meinAuthService, bootClazz);
        return bootLoader;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }


    @Override
    protected ExecutorService createExecutorService(ThreadFactory threadFactory) {
        return Executors.newCachedThreadPool(threadFactory);
    }
}
