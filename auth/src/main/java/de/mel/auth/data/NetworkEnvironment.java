package de.mel.auth.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;

import de.mel.Lok;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.sql.RWLock;

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
    private Map<Integer, UnknownAuthInstance> unknownAuthInstances = new HashMap<>();
    private RWLock unknownInstancesLock = new RWLock();

    public interface NetworkEnvironmentListener {
        void onUnknownFound(UnknownAuthInstance unknownAuthInstance);

        void onKownFound();
    }

    public static class UnknownAuthInstance {
        private final Integer portCert;
        private final Integer port;
        private final String address;
        private int hash;

        public UnknownAuthInstance(String address, Integer port, Integer portCert) {
            this.address = address;
            this.port = port;
            this.portCert = portCert;
            hash = 0;
            if (port != null)
                hash += port.hashCode();
            if (portCert != null)
                hash += portCert.hashCode();
            if (address != null)
                hash += address.hashCode();
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

        @Override
        public int hashCode() {
            return hash;
        }
    }

    public synchronized NetworkEnvironment add(Long certificateId, ServiceJoinServiceType service) {
        if (certificateId == null && service != null) {
            Lok.error("NetworkEnvironment.add: Cert==null, Service!=null");
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

    public Collection<UnknownAuthInstance> getUnknownAuthInstances() {
        return unknownAuthInstances.values();
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
        UnknownAuthInstance ins = new UnknownAuthInstance(address, port, portCert);
        boolean added = false;
        if (!unknownAuthInstances.containsKey(ins.hash)) {
            unknownAuthInstances.put(ins.hash, ins);
            added = true;
        }
        unknownInstancesLock.unlockWrite();
        if (added) {
            setChanged();
            notifyObservers();
        }
        return this;
    }

    public static class FoundServices {
        private final CertAddedListener certAddedListener;

        public List<ServiceJoinServiceType> get(Long certId) {
            return foundServices.get(certId);
        }

        public static interface CertAddedListener {
            void onCertAdded(Certificate certificate);
        }

        private Map<Long, List<ServiceJoinServiceType>> foundServices = new HashMap<>();

        public FoundServices(CertAddedListener certAddedListener) {
            this.certAddedListener = certAddedListener;
        }

        public void add(Certificate certificate, ServiceJoinServiceType service) {
            Long certId = certificate.getId().v();
            if (!foundServices.containsKey(certId)) {
                foundServices.put(certId, new ArrayList<>());
                if (certAddedListener != null)
                    certAddedListener.onCertAdded(certificate);
            }
            foundServices.get(certId).add(service);
        }
    }
}
