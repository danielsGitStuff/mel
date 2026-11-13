package de.mel.auth.service;

import de.mel.auth.MelNotification;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceNotificationManagerTest {

    @Test
    public void tracksActiveNotificationsAndAutoRemovesOnFinishAndCancel() {
        ServiceNotificationManager manager = new ServiceNotificationManager();
        List<String> events = new ArrayList<>();
        ServiceNotificationManager.ServiceStateListener listener = events::add;
        manager.addListener(listener);

        MelNotification boot = new MelNotification("svc-1", "boot", "Booting", "indexing");
        MelNotification sync = new MelNotification("svc-1", "sync", "Syncing", "downloading");

        manager.record(boot);
        manager.record(sync);
        assertEquals(2, manager.getActiveNotifications("svc-1").size());
        assertTrue(manager.getActiveNotifications("svc-2").isEmpty());

        boot.setProgress(10, 5, false);
        assertEquals(3, events.size());

        boot.finish();
        assertEquals(1, manager.getActiveNotifications("svc-1").size());
        assertSame(sync, manager.getActiveNotifications("svc-1").get(0));

        sync.cancel();
        assertTrue(manager.getActiveNotifications("svc-1").isEmpty());

        manager.removeListener(listener);
        int countBefore = events.size();
        manager.notifyServiceChanged("svc-1");
        assertEquals(countBefore, events.size());
    }

    @Test
    public void removeServiceClearsActiveNotifications() {
        ServiceNotificationManager manager = new ServiceNotificationManager();
        MelNotification boot = new MelNotification("svc-1", "boot", "Booting", "indexing");
        manager.record(boot);
        assertEquals(1, manager.getActiveNotifications("svc-1").size());

        manager.removeService("svc-1");
        assertTrue(manager.getActiveNotifications("svc-1").isEmpty());
    }
}
