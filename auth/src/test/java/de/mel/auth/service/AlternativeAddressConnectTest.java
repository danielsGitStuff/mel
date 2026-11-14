package de.mel.auth.service;

import de.mel.auth.data.db.CertificateAddress;
import de.mel.auth.socket.CannotConnectException;
import de.mel.auth.socket.CloseReason;
import de.mel.auth.socket.MelValidationProcess;
import de.mel.auth.tools.AddressParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.ServerSocket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.mel.auth.service.TwoInstances.ACCEPT_ALL;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@Tag("integration")
public class AlternativeAddressConnectTest {
    @TempDir
    File tempDir;

    private TwoInstances instances;

    @BeforeEach
    public void before() throws Exception {
        instances = new TwoInstances(tempDir, 33000, ACCEPT_ALL, ACCEPT_ALL);
    }

    @AfterEach
    public void after() {
        instances.close();
    }

    private static int findClosedPort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private void disconnectClientFromServer(long serverId) throws Exception {
        MelValidationProcess mvp = instances.client.getValidationProcess(serverId);
        if (mvp != null) {
            mvp.getMelAuthSocket().close(CloseReason.SHUTDOWN);
            assertTrue(TwoInstances.waitFor(() -> !instances.client.isConnectedTo(serverId), 10));
        }
    }

    @Test
    public void connectsViaFallbackAddressWhenPrimaryFails() throws Exception {
        instances.pair();
        long serverId = TwoInstances.idOf(instances.server, instances.client);
        disconnectClientFromServer(serverId);

        int deadPort = findClosedPort();
        instances.client.updateCertAddresses(serverId, "127.0.0.1", deadPort, deadPort + 1);
        instances.client.getCertificateManager().replaceAlternativeAddresses(
                serverId,
                Collections.singletonList(AddressParser.parse(
                        "127.0.0.1:" + instances.server.getSettings().getPort(),
                        instances.server.getSettings().getDeliveryPort()
                ))
        );

        MelValidationProcess connected = TwoInstances.connect(instances.client, instances.server);
        assertNotNull(connected);
        assertTrue(instances.client.isConnectedTo(serverId));

        List<CertificateAddress> stored = instances.client.getCertificateManager().getAlternativeAddresses(serverId);
        assertEquals(1, stored.size());
        assertEquals("127.0.0.1", stored.get(0).getAddress().v());
        assertEquals(instances.server.getSettings().getPort(), stored.get(0).getPort().v());
    }

    @Test
    public void settlesWithCannotConnectExceptionWhenAllCandidatesFail() throws Exception {
        instances.pair();
        long serverId = TwoInstances.idOf(instances.server, instances.client);
        disconnectClientFromServer(serverId);

        int deadPort1 = findClosedPort();
        int deadPort2 = findClosedPort();
        instances.client.updateCertAddresses(serverId, "127.0.0.1", deadPort1, deadPort1 + 1);
        instances.client.getCertificateManager().replaceAlternativeAddresses(
                serverId,
                Collections.singletonList(AddressParser.parse("127.0.0.1:" + deadPort2))
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        instances.client.connect(serverId)
                .onDone(p -> latch.countDown())
                .onError(e -> {
                    failure.set(e);
                    latch.countDown();
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        assertInstanceOf(CannotConnectException.class, failure.get());
    }
}
