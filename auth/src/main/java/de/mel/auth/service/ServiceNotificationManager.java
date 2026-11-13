package de.mel.auth.service;

import de.mel.auth.MelNotification;
import de.mel.auth.data.db.ServiceJoinServiceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks active {@link MelNotification}s per service UUID and notifies UI listeners
 * when a service's boot state or active notification progress changes.
 */
public class ServiceNotificationManager {

    public enum ServiceState {
        RUNNING,
        BOOTING,
        DEACTIVATED,
        ERROR
    }

    public interface ServiceStateListener {
        /**
         * @param serviceUuid UUID of the changed service, or null if all services may have changed
         */
        void onServiceStateChanged(String serviceUuid);
    }

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<MelNotification>> activeByService = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<ServiceStateListener> listeners = new CopyOnWriteArraySet<>();

    /**
     * Computes the current lifecycle state of a configured service.
     */
    public static ServiceState getServiceState(MelAuthService melAuthService, ServiceJoinServiceType service) {
        if (service.getActive().v() == null || !service.getActive().v()) {
            return ServiceState.DEACTIVATED;
        }
        MelService melService = melAuthService.getMelService(service.getUuid().v());
        if (melService == null) {
            return ServiceState.ERROR;
        }
        if (melService.getBootLevel() == melService.getReachedBootLevel()) {
            return ServiceState.RUNNING;
        }
        if (melService.getReachedBootLevel() == Bootloader.BootLevel.SHORT) {
            return ServiceState.BOOTING;
        }
        return ServiceState.ERROR;
    }

    public void record(MelNotification notification) {
        if (notification == null || notification.getServiceUuid() == null) {
            return;
        }
        if (notification.isCanceled() || notification.isFinished()) {
            return;
        }
        String uuid = notification.getServiceUuid();
        CopyOnWriteArrayList<MelNotification> list = activeByService.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
        if (!list.contains(notification)) {
            list.add(notification);
        }
        notification.addProgressListener(new MelNotification.MelProgressListener() {
            @Override
            public void onProgress(MelNotification n, int max, int current, boolean indeterminate) {
                notifyServiceChanged(uuid);
            }

            @Override
            public void onCancel(MelNotification n) {
                removeNotification(uuid, n, this);
            }

            @Override
            public void onFinish(MelNotification n) {
                removeNotification(uuid, n, this);
            }
        });
        notifyServiceChanged(uuid);
    }

    private void removeNotification(String uuid, MelNotification notification, MelNotification.MelProgressListener self) {
        notification.removeProgressListener(self);
        CopyOnWriteArrayList<MelNotification> list = activeByService.get(uuid);
        if (list != null) {
            list.remove(notification);
            if (list.isEmpty()) {
                activeByService.remove(uuid, list);
            }
        }
        notifyServiceChanged(uuid);
    }

    public List<MelNotification> getActiveNotifications(String serviceUuid) {
        if (serviceUuid == null) {
            return Collections.emptyList();
        }
        CopyOnWriteArrayList<MelNotification> list = activeByService.get(serviceUuid);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    public void removeService(String serviceUuid) {
        if (serviceUuid == null) {
            return;
        }
        activeByService.remove(serviceUuid);
        notifyServiceChanged(serviceUuid);
    }

    public void notifyServiceChanged(String serviceUuid) {
        for (ServiceStateListener listener : listeners) {
            listener.onServiceStateChanged(serviceUuid);
        }
    }

    public void addListener(ServiceStateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(ServiceStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
