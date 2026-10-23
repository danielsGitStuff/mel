package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.broadcast.MelAuthBrotCaster;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.data.NetworkEnvironment;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import de.mel.auth.tools.CountLock;
import de.mel.auth.tools.Promises;
import fun.with.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two instances that do not know each other find each other by multicast: the one that discovers lists the other as unknown.
 */
@Tag("integration")
public class DiscoveryTest {
    private static final int TIMEOUT_SECONDS = 20;
    private static final int BASE = 34000;

    @TempDir
    File tempDir;

    private MelAuthService server;
    private MelAuthService client;

    private MelAuthService boot(String name, int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        File dir = new File(tempDir, name);
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(port).setDeliveryPort(deliveryPort)
                .setBrotcastListenerPort(brotcastListenerPort).setBrotcastPort(brotcastPort)
                .setWorkingDirectory(dir).setName(name).setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        AtomicReference<MelAuthService> booted = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        new MelBoot(settings, new PowerManager(settings)).boot().onDone(service -> {
            booted.set(service);
            latch.countDown();
        }).onError(e -> {
            failure.set(e);
            latch.countDown();
        });
        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), name + " did not boot in time");
        assertNull(failure.get(), name + " failed to boot");
        return booted.get();
    }

    @AfterEach
    public void after() {
        CountLock lock = new CountLock().lock();
        List<Promise<?, ?>> shut = new ArrayList<>();
        for (MelAuthService service : new MelAuthService[]{client, server}) {
            if (service != null)
                try {
                    shut.add(service.shutDown());
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        Promises.allSettled(shut.toArray(new Promise[0])).onDone(r -> lock.unlock());
        lock.lock();
    }

    @Test
    public void anUnknownInstanceIsFound() throws Exception {
        start(BASE);
        client.discoverNetworkEnvironment();
        assertServerFound(BASE);
    }

    /**
     * Android suspends and resumes the instance when power or wifi change.
     */
    @Test
    public void anUnknownInstanceIsFoundAfterSuspendAndResume() throws Exception {
        start(BASE + 10);
        MelAuthBrotCaster beforeSuspend = client.getBrotCaster();
        client.suspend();
        client.resume();
        // resume makes a new brotcaster; an answer that comes before it listens is lost
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!resumedBrotCasterListens(beforeSuspend) && System.nanoTime() < end)
            Thread.sleep(50);
        assertTrue(resumedBrotCasterListens(beforeSuspend), "the client's discovery did not start again");
        client.getNetworkEnvironment().clear();
        client.discoverNetworkEnvironment();
        assertServerFound(BASE + 10);
    }

    private boolean resumedBrotCasterListens(MelAuthBrotCaster beforeSuspend) {
        MelAuthBrotCaster current = client.getBrotCaster();
        return current != null && current != beforeSuspend && current.getStartedDeferred().hasResult();
    }

    private void start(int base) throws Exception {
        AbstractFile.configure(new DefaultFileConfiguration());
        server = boot("discovery-SERVER", base, base + 1, base + 4, base + 5);
        client = boot("discovery-CLIENT", base + 2, base + 3, base + 5, base + 4);
    }

    private void assertServerFound(int base) throws Exception {
        NetworkEnvironment environment = client.getNetworkEnvironment();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (environment.getUnknownAuthInstances().isEmpty() && System.nanoTime() < end)
            Thread.sleep(50);
        assertEquals(1, environment.getUnknownAuthInstances().size(), "the server was not found");
        NetworkEnvironment.UnknownAuthInstance found = environment.getUnknownAuthInstances().iterator().next();
        assertEquals(base, found.getPort());
        assertEquals(base + 1, found.getPortCert());
    }
}
