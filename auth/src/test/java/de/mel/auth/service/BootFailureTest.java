package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import fun.with.promise.Promise;
import fun.with.promise.PromiseImpl;
import de.mel.DeferredRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the boot promise does when a part of the auth worker (socket opener, certificate delivery, brotcaster) does not start.
 * MelAuthWorker waits for the three of them. A part that does not start rejects {@link MelBoot#boot()} with a
 * {@link MelAuthStartException} that names the port, after the half started instance was shut down: whatever it had bound is
 * free again and the same instance can boot again right away.
 */
@Tag("integration")
public class BootFailureTest {
    /**
     * how long a boot that should settle may take (a healthy one takes a few hundred milliseconds)
     */
    private static final int WAIT_FOR_BOOT_MILLIS = TwoInstances.TIMEOUT_SECONDS * 1000;
    private static final AtomicInteger nextPortBase = new AtomicInteger(21000);

    @TempDir
    File tempDir;

    private MelBoot melBoot;
    private ServerSocket blocker;
    private MelAuthServiceImpl directService;
    private Outcome lastOutcome;

    @BeforeEach
    public void before() {
        AbstractFile.configure(new DefaultFileConfiguration());
    }

    @AfterEach
    public void after() throws Exception {
        if (blocker != null)
            blocker.close();
        if (directService != null) {
            CountDownLatch latch = new CountDownLatch(1);
            directService.shutDown().onDone(r -> latch.countDown()).onError(r -> latch.countDown());
            latch.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        if (melBoot != null && (lastOutcome == null || lastOutcome.failure.get() == null)) {
            // a failed boot shut its instance down already; the others never reached this test, so get them from where MelBoot keeps them
            Field field = MelBoot.class.getDeclaredField("melAuthService");
            field.setAccessible(true);
            MelAuthService service = (MelAuthService) field.get(melBoot);
            if (service != null) {
                CountDownLatch latch = new CountDownLatch(1);
                service.shutDown().onDone(r -> latch.countDown()).onError(r -> latch.countDown());
                latch.await(TwoInstances.TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    private static class Outcome {
        final CountDownLatch settled = new CountDownLatch(1);
        final AtomicReference<MelAuthService> booted = new AtomicReference<>();
        final AtomicReference<Exception> failure = new AtomicReference<>();
    }

    private Outcome boot(int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        return boot("bootfailure", port, deliveryPort, brotcastListenerPort, brotcastPort);
    }

    private Outcome boot(String dirName, int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        File dir = new File(tempDir, dirName);
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(port).setDeliveryPort(deliveryPort)
                .setBrotcastListenerPort(brotcastListenerPort).setBrotcastPort(brotcastPort)
                .setWorkingDirectory(dir).setName("bootfailure").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        melBoot = new MelBoot(settings, new PowerManager(settings));
        Outcome outcome = new Outcome();
        lastOutcome = outcome;
        Promise<MelAuthService, Exception> promise = melBoot.boot();
        promise.onDone(service -> {
            outcome.booted.set(service);
            outcome.settled.countDown();
        }).onError(e -> {
            outcome.failure.set(e);
            outcome.settled.countDown();
        });
        return outcome;
    }

    @Test
    public void healthyBootSettlesQuickly() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        Outcome outcome = boot(base, base + 1, base + 4, base + 5);
        assertTrue(outcome.settled.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS), "the control case did not boot");
        assertNotNull(outcome.booted.get());
        assertNull(outcome.failure.get());
    }

    private static boolean isFree(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.bind(new InetSocketAddress(port));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static ServerSocket take(int port) throws Exception {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(port));
        return socket;
    }

    private void assertRejected(Outcome outcome, String expectedInMessage) throws Exception {
        assertTrue(outcome.settled.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS), "boot never settled");
        assertNull(outcome.booted.get(), "boot resolved although a part could not start");
        Exception failure = outcome.failure.get();
        assertInstanceOf(MelAuthStartException.class, failure);
        assertTrue(failure.getMessage().contains(expectedInMessage),
                "the reason does not say '" + expectedInMessage + "': " + failure.getMessage());
    }

    @Test
    public void bootFailsWhenThePortIsTaken() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        // somebody else (another Mel instance, for example) listens on our port
        blocker = take(base);
        Outcome outcome = boot(base, base + 1, base + 4, base + 5);

        assertRejected(outcome, "port " + base);
        // the certificate delivery did start; the shut down freed it before the caller was told
        assertTrue(isFree(base + 1), "the certificate delivery port is still bound");
    }

    @Test
    public void bootFailsWhenTheCertificateDeliveryPortIsTaken() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        blocker = take(base + 1);
        Outcome outcome = boot(base, base + 1, base + 4, base + 5);

        assertRejected(outcome, "port " + (base + 1));
        assertTrue(isFree(base), "the connection port is still bound");
    }

    @Test
    public void bootFailsWhenABrotcasterCannotStart() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        // not a port at all: the brotcaster rejects its start
        Outcome outcome = boot(base, base + 1, 70000, base + 5);

        assertRejected(outcome, "port 70000");
        assertTrue(isFree(base), "the connection port is still bound");
        assertTrue(isFree(base + 1), "the certificate delivery port is still bound");
    }

    /**
     * After a failed boot the same working directory (database, keys) and the same ports can be used again: the user closes the
     * other program and starts Mel again.
     */
    @Test
    public void aFailedBootCanBeRepeatedOnceThePortIsFree() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        blocker = take(base);
        Outcome failed = boot("again", base, base + 1, base + 4, base + 5);
        assertRejected(failed, "port " + base);

        blocker.close();
        blocker = null;
        Outcome again = boot("again", base, base + 1, base + 4, base + 5);
        assertTrue(again.settled.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS), "the second boot never settled");
        assertNull(again.failure.get(), "the second boot failed");
        assertNotNull(again.booted.get());
    }

    private static class ThrowsWhenToldAuthIsUp extends MelTestService {
        final AtomicInteger told = new AtomicInteger();

        ThrowsWhenToldAuthIsUp(MelAuthService melAuthService, File workingDirectory) {
            super(melAuthService, workingDirectory, 1L, "throws when told");
        }

        @Override
        public void onServiceRegistered() {
            told.incrementAndGet();
            throw new IllegalStateException("this service does not like auth being up");
        }
    }

    /**
     * MelAuthWorker resolves its start in the catch block when saying hello fails and, falling through, a second time right
     * after it. The second one is a settle of a settled promise. The start must count as done anyway, only once.
     */
    @Test
    public void startIsDoneAlthoughAServiceThrowsWhenToldThatAuthIsUp() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        File dir = new File(tempDir, "direct");
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(base).setDeliveryPort(base + 1)
                .setBrotcastListenerPort(base + 4).setBrotcastPort(base + 5)
                .setWorkingDirectory(dir).setName("direct").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        PowerManager powerManager = new PowerManager(settings);
        // the service needs a MelBoot to execute its runnables, but MelBoot.boot() is not what is tested here
        melBoot = new MelBoot(settings, powerManager);
        directService = new MelAuthServiceImpl(settings, powerManager);
        directService.setMelBoot(melBoot);
        PromiseImpl<DeferredRunnable, Exception> started = directService.prepareStart();
        ThrowsWhenToldAuthIsUp service = new ThrowsWhenToldAuthIsUp(directService, new File(dir, "service"));
        directService.registerMelService(service);
        assertEquals(0, service.told.get(), "registered before the start, nobody tells it yet");

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Boolean> rejected = new AtomicReference<>(false);
        started.onDone(r -> done.countDown()).onError(e -> {
            rejected.set(true);
            done.countDown();
        });
        directService.start();

        assertTrue(done.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS), "start never settled");
        assertFalse(rejected.get(), "start was rejected");
        assertTrue(started.hasResult());
        assertEquals(1, service.told.get());
    }
}
