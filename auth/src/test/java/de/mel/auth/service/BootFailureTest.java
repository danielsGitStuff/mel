package de.mel.auth.service;

import de.mel.auth.MelStrings;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.DefaultFileConfiguration;
import de.mel.auth.service.power.PowerManager;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
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
 * MelAuthWorker waits for the three of them. This pins down the current behaviour, which is not what one would design:
 * a failing start does not reach the caller of {@link MelBoot#boot()}, the promise just stays pending.
 * Whoever changes that, changes these tests on purpose.
 */
@Tag("integration")
public class BootFailureTest {
    /**
     * long enough for a healthy boot (a few hundred milliseconds), short enough to keep the tests fast
     */
    private static final int WAIT_FOR_BOOT_MILLIS = 6000;
    private static final AtomicInteger nextPortBase = new AtomicInteger(21000);

    @TempDir
    File tempDir;

    private MelBoot melBoot;
    private ServerSocket blocker;
    private MelAuthServiceImpl directService;

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
            directService.shutDown().done(r -> latch.countDown()).fail(r -> latch.countDown());
            latch.await(30, TimeUnit.SECONDS);
        }
        if (melBoot != null) {
            // the instance never reached the caller of boot(), so get it from where MelBoot keeps it
            Field field = MelBoot.class.getDeclaredField("melAuthService");
            field.setAccessible(true);
            MelAuthService service = (MelAuthService) field.get(melBoot);
            if (service != null) {
                CountDownLatch latch = new CountDownLatch(1);
                service.shutDown().done(r -> latch.countDown()).fail(r -> latch.countDown());
                latch.await(30, TimeUnit.SECONDS);
            }
        }
    }

    private static class Outcome {
        final CountDownLatch settled = new CountDownLatch(1);
        final AtomicReference<MelAuthService> booted = new AtomicReference<>();
        final AtomicReference<Exception> failure = new AtomicReference<>();
    }

    private Outcome boot(int port, int deliveryPort, int brotcastListenerPort, int brotcastPort) throws Exception {
        File dir = new File(tempDir, "bootfailure");
        dir.mkdirs();
        MelAuthSettings settings = new MelAuthSettings().setPort(port).setDeliveryPort(deliveryPort)
                .setBrotcastListenerPort(brotcastListenerPort).setBrotcastPort(brotcastPort)
                .setWorkingDirectory(dir).setName("bootfailure").setVariant(MelStrings.update.VARIANT_JAR);
        settings.setJsonFile(new File(dir, MelBoot.Companion.getDEFAULT_SETTINGS_FILE_NAME()));
        melBoot = new MelBoot(settings, new PowerManager(settings));
        Outcome outcome = new Outcome();
        Promise<MelAuthService, Exception, Void> promise = melBoot.boot();
        promise.done(service -> {
            outcome.booted.set(service);
            outcome.settled.countDown();
        }).fail(e -> {
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

    @Test
    public void bootNeverSettlesWhenThePortIsTaken() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        // somebody else (another Mel instance, for example) listens on our port
        blocker = new ServerSocket();
        blocker.bind(new InetSocketAddress(base));
        Outcome outcome = boot(base, base + 1, base + 4, base + 5);

        // the socket opener only logs that it failed, it neither resolves nor rejects its start
        assertFalse(outcome.settled.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS),
                "boot settled although the port was taken: booted=" + outcome.booted.get() + " failure=" + outcome.failure.get());
    }

    @Test
    public void bootNeverSettlesWhenABrotcasterCannotStart() throws Exception {
        int base = nextPortBase.getAndAdd(10);
        // not a port at all: the brotcaster rejects its start, the worker rejects its own ("keinen plan von nix"),
        // but MelBoot only listens for success
        Outcome outcome = boot(base, base + 1, 70000, base + 5);

        assertFalse(outcome.settled.await(WAIT_FOR_BOOT_MILLIS, TimeUnit.MILLISECONDS),
                "boot settled although the brotcaster could not start: booted=" + outcome.booted.get() + " failure=" + outcome.failure.get());
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
        DeferredObject<DeferredRunnable, Exception, Void> started = directService.prepareStart();
        ThrowsWhenToldAuthIsUp service = new ThrowsWhenToldAuthIsUp(directService, new File(dir, "service"));
        directService.registerMelService(service);
        assertEquals(0, service.told.get(), "registered before the start, nobody tells it yet");

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Boolean> rejected = new AtomicReference<>(false);
        started.done(r -> done.countDown()).fail(e -> {
            rejected.set(true);
            done.countDown();
        });
        directService.start();

        assertTrue(done.await(WAIT_FOR_BOOT_MILLIS * 5L, TimeUnit.MILLISECONDS), "start never settled");
        assertFalse(rejected.get(), "start was rejected");
        assertTrue(started.isResolved());
        assertEquals(1, service.told.get());
    }
}
