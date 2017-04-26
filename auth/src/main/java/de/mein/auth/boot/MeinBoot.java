package de.mein.auth.boot;

import de.mein.DeferredRunnable;
import de.mein.MeinRunnable;
import de.mein.MeinThread;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.data.access.DatabaseManager;
import de.mein.auth.data.db.Service;
import de.mein.auth.data.db.ServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.sql.SqlQueriesException;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Boots up the MeinAuth instance and all existing services by calling the corresponding bootloaders.
 */
public class MeinBoot implements MeinRunnable {
    private static Logger logger = Logger.getLogger(MeinBoot.class.getName());
    private static Set<Class<? extends BootLoader>> bootloaderClasses = new HashSet<>();
    private static Map<String, Class<? extends BootLoader>> bootloaderMap = new HashMap<>();
    public static final File defaultWorkingDir1 = new File("meinauth.workingdir.1");
    public static final File defaultWorkingDir2 = new File("meinauth.workingdir.2");
    private DeferredObject<MeinAuthService, Exception, Void> deferredObject;
    private MeinAuthSettings meinAuthSettings;
    private MeinAuthService meinAuthService;
    private ExecutorService executorService;
    private Semaphore threadSemaphore = new Semaphore(1, true);
    private LinkedList<MeinThread> threadQueue = new LinkedList<>();

    public static void addBootLoaderClass(Class<? extends BootLoader> clazz) {
        bootloaderClasses.add(clazz);
    }

    public static Map<String, Class<? extends BootLoader>> getBootloaderMap() {
        return bootloaderMap;
    }

    public static Set<Class<? extends BootLoader>> getBootloaderClasses() {
        return bootloaderClasses;
    }

    private void prepareBoot(MeinAuthSettings meinAuthSettings) {
        this.deferredObject = new DeferredObject<>();
        this.meinAuthSettings = meinAuthSettings;
        this.executorService = Executors.newCachedThreadPool(r -> {
            MeinThread meinThread = null;
            try {
                threadSemaphore.acquire();
                meinThread = threadQueue.poll();
                threadSemaphore.release();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return meinThread;
        });
        execute(this);
    }


    public void execute(MeinRunnable runnable) {
        try {
            threadSemaphore.acquire();
            threadQueue.add(new MeinThread(runnable));
            threadSemaphore.release();
            executorService.execute(runnable);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public Promise<MeinAuthService, Exception, Void> boot(MeinAuthService meinAuthService) throws Exception {
        meinAuthService.setMeinBoot(this);
        prepareBoot(meinAuthService.getSettings());
        this.meinAuthService = meinAuthService;
        return deferredObject;
    }

    public static void main(String[] args) {
        // MeinAuthService meinAuthService =
    }

    @Override
    public void run() {
        try {
            if (meinAuthService == null)
                meinAuthService = new MeinAuthService(meinAuthSettings);
            DeferredObject<DeferredRunnable, Exception, Void> promiseAuthBooted = meinAuthService.start();
            DatabaseManager databaseManager = meinAuthService.getDatabaseManager();
            List<BootLoader> bootLoaders = new ArrayList<>();
            for (Class<? extends BootLoader> bootClass : bootloaderClasses) {
                logger.log(Level.FINE, "MeinBoot.boot.booting: " + bootClass.getCanonicalName());
                BootLoader bootLoader = createBootLoader(meinAuthService, bootClass);
                bootloaderMap.put(bootLoader.getName(), bootClass);
                bootLoaders.add(bootLoader);
            }
            for (BootLoader bootLoader : bootLoaders) {
                List<Service> services = meinAuthService.getDatabaseManager().getServicesByType(bootLoader.getTypeId());
                bootLoader.boot(meinAuthService, services);
            }
            promiseAuthBooted.done(result -> {
                deferredObject.resolve(meinAuthService);
            });
        } catch (Exception e) {
            e.printStackTrace();
            deferredObject.reject(e);
        }
    }

    public static BootLoader createBootLoader(MeinAuthService meinAuthService, Class<? extends BootLoader> bootClass) throws SqlQueriesException, IllegalAccessException, InstantiationException {
        BootLoader bootLoader = bootClass.newInstance();
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

    public static BootLoader getBootLoader(MeinAuthService meinAuthService, String typeName) throws IllegalAccessException, SqlQueriesException, InstantiationException {
        Class<? extends BootLoader> bootClazz = bootloaderMap.get(typeName);
        BootLoader bootLoader = createBootLoader(meinAuthService, bootClazz);
        return bootLoader;
    }

    @Override
    public String getRunnableName() {
        return getClass().getSimpleName();
    }

    public void shutDown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(2000, TimeUnit.MILLISECONDS);
    }
}
