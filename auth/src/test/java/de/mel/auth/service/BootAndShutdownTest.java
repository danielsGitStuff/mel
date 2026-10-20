package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.db.Service;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How services are booted in two levels and how everything is shut down again.
 * Both are built on "wait for a list of promises" (MelBoot.bootServices, MelAuthServiceImpl.shutDown), with contracts
 * that are easy to lose when the promise implementation changes:
 * an empty list resolves at once, a promise that is settled already still calls its callbacks, a service that fails or
 * returns no promise must not keep the others from shutting down.
 */
public class BootAndShutdownTest {
    private static final int TIMEOUT_SECONDS = 30;
    private static final AtomicInteger nextPortBase = new AtomicInteger(20000);

    /**
     * lets the test decide whether heavy work (the second boot level) is allowed
     */
    private static class ControllablePowerManager extends PowerManager {
        volatile boolean heavyWork;

        ControllablePowerManager(MelAuthSettings settings) {
            super(settings);
        }

        @Override
        protected boolean heavyWorkAllowedNoLock() {
            return heavyWork;
        }
    }

    public enum LongBoot {
        OK, REJECT, THROW
    }

    public static class BootTestService extends MelTestService {
        final CountDownLatch longBootFinished = new CountDownLatch(1);

        BootTestService(MelAuthService melAuthService, File workingDirectory, Long serviceTypeId, String uuid) {
            super(melAuthService, workingDirectory, serviceTypeId, uuid, Bootloader.BootLevel.LONG);
        }

        @Override
        public void onBootLevel2Finished() {
            longBootFinished.countDown();
        }
    }

    /**
     * Is created by MelBoot with newInstance(), several times, so what happens is recorded in static fields.
     */
    public static class BootTestBootloader extends Bootloader<BootTestService> {
        static final String NAME = "boot test service";
        static final AtomicInteger shortBoots = new AtomicInteger();
        static final AtomicInteger longBoots = new AtomicInteger();
        static final AtomicReference<BootTestService> booted = new AtomicReference<>();
        static volatile LongBoot longBoot = LongBoot.OK;

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public String getDescription() {
            return "only here to see how booting works";
        }

        @Override
        public BootTestService bootLevelShortImpl(MelAuthService melAuthService, Service serviceDescription) throws BootException {
            shortBoots.incrementAndGet();
            try {
                File dir = new File(bootLoaderDir, serviceDescription.getUuid().v());
                dir.mkdirs();
                BootTestService service = new BootTestService(melAuthService, dir, typeId, serviceDescription.getUuid().v());
                booted.set(service);
                melAuthService.registerMelService(service);
                return service;
            } catch (Exception e) {
                throw new BootException(this, e);
            }
        }

        @Override
        public Promise<Void, BootException, Void> bootLevelLongImpl() throws BootException {
            longBoots.incrementAndGet();
            switch (longBoot) {
                case THROW:
                    throw new BootException(this, "long boot threw");
                case REJECT:
                    // settled before the caller gets it
                    DeferredObject<Void, BootException, Void> rejected = new DeferredObject<>();
                    rejected.reject(new BootException(this, "long boot rejected"));
                    return rejected;
                default:
                    DeferredObject<Void, BootException, Void> resolved = new DeferredObject<>();
                    resolved.resolve(null);
                    return resolved;
            }
        }

        @Override
        public void cleanUpDeletedService(BootTestService melService, String uuid) {
        }

        @Override
        public boolean isCompatiblePartner(ServiceJoinServiceType service) {
            return false;
        }
    }

    public enum Shutdown {
        /**
         * resolved promise
         */
        OK,
        /**
         * rejected promise
         */
        REJECTED,
        /**
         * no promise at all, like several real services
         */
        NO_PROMISE,
        /**
         * does not even return
         */
        THROWS
    }

    private static class ShutdownProbe extends MelTestService {
        final Shutdown behavior;
        final AtomicInteger shutDowns = new AtomicInteger();

        ShutdownProbe(MelAuthService melAuthService, File workingDirectory, String uuid, Shutdown behavior) {
            super(melAuthService, workingDirectory, 1L, uuid);
            this.behavior = behavior;
        }

        @Override
        public Promise<Void, Void, Void> onShutDown() {
            shutDowns.incrementAndGet();
            DeferredObject<Void, Void, Void> promise = new DeferredObject<>();
            switch (behavior) {
                case OK:
                    return promise.resolve(null);
                case REJECTED:
                    return promise.reject(null);
                case THROWS:
                    throw new IllegalStateException("shutdown of " + getUuid() + " went wrong");
                default:
                    return null;
            }
        }
    }

    @TempDir
    File tempDir;

    private ControllablePowerManager powerManager;
    private MelAuthService auth;

    @BeforeEach
    public void before() {
        BootTestBootloader.shortBoots.set(0);
        BootTestBootloader.longBoots.set(0);
        BootTestBootloader.booted.set(null);
        BootTestBootloader.longBoot = LongBoot.OK;
    }

    private void boot(boolean heavyWork) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        int base = nextPortBase.getAndAdd(10);
        File dir = new File(tempDir, "boottest");
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(base).setDeliveryPort(base + 1)
                .setBrotcastListenerPort(base + 4).setBrotcastPort(base + 5)
                .setWorkingDirectory(dir).setName("boottest").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        powerManager = new ControllablePowerManager(settings);
        powerManager.heavyWork = heavyWork;
        AtomicReference<MelAuthService> booted = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        new MelBoot(settings, powerManager, BootTestBootloader.class).boot().done(service -> {
            booted.set(service);
            latch.countDown();
        }).fail(e -> {
            failure.set(e);
            latch.countDown();
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "did not boot in time");
        assertNull(failure.get(), "failed to boot");
        auth = booted.get();
    }

    /**
     * the service exists in the database only, MelBoot has to find and boot it
     */
    private Service createServiceInDatabase() throws Exception {
        Long typeId = auth.getDatabaseManager().getServiceTypeByName(BootTestBootloader.NAME).getId().v();
        return auth.getDatabaseManager().createService(typeId, "service to boot");
    }

    /**
     * @return whether the service shut down: resolved, or failed (which would be a bug for a shutdown)
     */
    private boolean shutDownAndWaitForResolve() throws Exception {
        AtomicReference<Boolean> resolved = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        MelAuthService shutting = auth;
        auth = null;
        shutting.shutDown().done(r -> {
            resolved.set(true);
            latch.countDown();
        }).fail(r -> {
            resolved.set(false);
            latch.countDown();
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "shutDown() never settled");
        return resolved.get();
    }

    @AfterEach
    public void after() throws Exception {
        if (auth != null)
            shutDownAndWaitForResolve();
    }

    @Test
    public void bootWithoutServicesFinishes() throws Exception {
        boot(true);
        // bootServices() waits for an empty list of promises: that has to resolve at once
        assertEquals(0, BootTestBootloader.shortBoots.get());
        assertEquals(0, BootTestBootloader.longBoots.get());
    }

    @Test
    public void bothBootLevelsRunWhenHeavyWorkIsAllowed() throws Exception {
        boot(true);
        Service service = createServiceInDatabase();
        auth.getMelBoot().bootServices();

        assertEquals(1, BootTestBootloader.shortBoots.get());
        assertEquals(1, BootTestBootloader.longBoots.get());
        assertNotNull(auth.getMelService(service.getUuid().v()), "the booted service is not registered");
        assertTrue(BootTestBootloader.booted.get().longBootFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void secondBootLevelWaitsUntilHeavyWorkIsAllowed() throws Exception {
        boot(false);
        Service service = createServiceInDatabase();
        auth.getMelBoot().bootServices();

        assertEquals(1, BootTestBootloader.shortBoots.get());
        assertEquals(0, BootTestBootloader.longBoots.get(), "heavy work ran although it was not allowed");
        assertNotNull(auth.getMelService(service.getUuid().v()), "the first boot level must already register the service");
        assertEquals(1L, BootTestBootloader.booted.get().longBootFinished.getCount());

        auth.getMelBoot().onHeavyWorkAllowed();
        assertEquals(0, BootTestBootloader.longBoots.get(), "still not allowed");

        powerManager.heavyWork = true;
        auth.getMelBoot().onHeavyWorkAllowed();
        assertEquals(1, BootTestBootloader.longBoots.get());
        assertTrue(BootTestBootloader.booted.get().longBootFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // everything outstanding was done, asking again must not boot again
        auth.getMelBoot().onHeavyWorkAllowed();
        assertEquals(1, BootTestBootloader.longBoots.get());
        assertEquals(1, BootTestBootloader.shortBoots.get());
    }

    @Test
    public void servicesThatAreBootedAlreadyAreNotBootedAgain() throws Exception {
        boot(true);
        createServiceInDatabase();
        auth.getMelBoot().bootServices();
        auth.getMelBoot().bootServices();
        assertEquals(1, BootTestBootloader.shortBoots.get());
        assertEquals(1, BootTestBootloader.longBoots.get());
    }

    private void assertBootErrorIsRecorded(Service service) throws Exception {
        assertNull(auth.getMelService(service.getUuid().v()), "a service whose boot failed must not stay registered");
        Service stored = auth.getDatabaseManager().getServiceByUuid(service.getUuid().v());
        assertNotNull(stored.getLastError(), "the boot error was not stored with the service");
        assertFalse(BootTestBootloader.booted.get().longBootFinished.await(1, TimeUnit.SECONDS), "a failed boot counts as finished");
    }

    @Test
    public void rejectedSecondBootLevelIsRecorded() throws Exception {
        BootTestBootloader.longBoot = LongBoot.REJECT;
        boot(true);
        Service service = createServiceInDatabase();
        auth.getMelBoot().bootServices();

        assertEquals(1, BootTestBootloader.longBoots.get());
        assertBootErrorIsRecorded(service);
    }

    @Test
    public void throwingSecondBootLevelIsRecorded() throws Exception {
        BootTestBootloader.longBoot = LongBoot.THROW;
        boot(true);
        Service service = createServiceInDatabase();
        auth.getMelBoot().bootServices();

        assertEquals(1, BootTestBootloader.longBoots.get());
        assertBootErrorIsRecorded(service);
    }

    private ShutdownProbe register(String name, Shutdown behavior) throws Exception {
        File dir = new File(tempDir, name);
        dir.mkdirs();
        ShutdownProbe probe = new ShutdownProbe(auth, dir, name, behavior);
        auth.registerMelService(probe);
        return probe;
    }

    @Test
    public void shutDownResolvesWhenEveryServiceShutsDown() throws Exception {
        boot(false);
        ShutdownProbe a = register("a", Shutdown.OK);
        ShutdownProbe b = register("b", Shutdown.OK);
        assertTrue(shutDownAndWaitForResolve());
        assertEquals(1, a.shutDowns.get());
        assertEquals(1, b.shutDowns.get());
    }

    @Test
    public void shutDownResolvesAlthoughAServiceFailsToShutDown() throws Exception {
        boot(false);
        ShutdownProbe ok = register("ok", Shutdown.OK);
        ShutdownProbe rejected = register("rejected", Shutdown.REJECTED);
        ShutdownProbe other = register("other", Shutdown.OK);
        assertTrue(shutDownAndWaitForResolve(), "one failing service must not make the whole shutdown fail");
        assertEquals(1, ok.shutDowns.get());
        assertEquals(1, rejected.shutDowns.get());
        assertEquals(1, other.shutDowns.get());
    }

    @Test
    public void shutDownResolvesAlthoughAServiceReturnsNoPromise() throws Exception {
        boot(false);
        ShutdownProbe silent = register("silent", Shutdown.NO_PROMISE);
        ShutdownProbe other = register("other", Shutdown.OK);
        assertTrue(shutDownAndWaitForResolve());
        assertEquals(1, silent.shutDowns.get());
        assertEquals(1, other.shutDowns.get());
    }

    @Test
    public void shutDownResolvesAlthoughAServiceThrows() throws Exception {
        boot(false);
        ShutdownProbe throwing = register("throwing", Shutdown.THROWS);
        ShutdownProbe other = register("other", Shutdown.OK);
        assertTrue(shutDownAndWaitForResolve());
        assertEquals(1, throwing.shutDowns.get());
        assertEquals(1, other.shutDowns.get(), "a service that throws kept another one from shutting down");
    }

    @Test
    public void shutDownResolvesWithoutServices() throws Exception {
        boot(false);
        assertTrue(shutDownAndWaitForResolve());
    }
}
