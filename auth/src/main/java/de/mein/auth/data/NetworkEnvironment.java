package de.mein.auth.data;

import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.sql.RWLock;

import java.util.*;

/**
 * Created by xor on 01.10.2016.
 */
public class NetworkEnvironment extends Observable {

    /**
     * Key: "cert.name"."service.name"
     */
    private Map<String, ServiceJoinServiceType> serviceMap = new HashMap<>();
    private Map<Long, List<ServiceJoinServiceType>> certificateServicesMap = new HashMap<>();
    private Map<ServiceJoinServiceType, Long> serviceCertificateMap = new HashMap<>();
    private List<UnknownAuthInstance> unknownAuthInstances = new ArrayList<>();
    private RWLock unknownInstancesLock = new RWLock();
    public interface NetworkEnvironmentListener{
        void onUnknownFound(UnknownAuthInstance unknownAuthInstance);
        void onKownFound();
    }
    public static class UnknownAuthInstance {
        private final int portCert;
        private final int port;
        private final String address;

        public UnknownAuthInstance(String address, int port, int portCert) {
            this.address = address;
            this.port = port;
            this.portCert = portCert;
        }

        public int getPort() {
            return port;
        }

        public int getPortCert() {
            return portCert;
        }

        public String getAddress() {
            return address;
        }
    }

    public synchronized NetworkEnvironment add(Long certificateId, ServiceJoinServiceType service) {
        if (certificateId == null && service != null) {
            System.err.println("NetworkEnvironment.add: Cert==null, Service!=null");
            return this;
        }
        if (!certificateServicesMap.containsKey(certificateId)) {
            certificateServicesMap.put(certificateId, new ArrayList<>());
            setChanged();
        }
        if (service != null) {
            String key = certificateId + "." + service.getUuid().v();
            if (!serviceMap.containsKey(key)) {
                certificateServicesMap.get(certificateId).add(service);
                serviceCertificateMap.put(service, certificateId);
                serviceMap.put(key, service);
                setChanged();
            }
        }
        notifyObservers();
        return this;
    }

    public List<UnknownAuthInstance> getUnknownAuthInstances() {
        return unknownAuthInstances;
    }

    public Long getCertificateId(ServiceJoinServiceType service) {
        return serviceCertificateMap.get(service);
    }

    public List<ServiceJoinServiceType> getServices(Long certificateId) {
        return certificateServicesMap.get(certificateId);
    }

    public Collection<ServiceJoinServiceType> getServices() {
        return serviceMap.values();
    }

    public Set<Long> getCertificateIds() {
        return certificateServicesMap.keySet();
    }

    public NetworkEnvironment clear() {
        serviceMap.clear();
        certificateServicesMap.clear();
        serviceCertificateMap.clear();
        unknownAuthInstances.clear();
        return this;
    }

    public NetworkEnvironment addUnkown(String address, int port, int portCert) {
        unknownInstancesLock.lockWrite();
        unknownAuthInstances.add(new UnknownAuthInstance(address, port, portCert));
        unknownInstancesLock.unlockWrite();
        setChanged();
        notifyObservers();
        return this;
    }
}
