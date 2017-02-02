package de.mein.drive.data;

import de.mein.core.serialize.SerializableEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by xor on 10/26/16.
 */
public class DriveServerSettingsDetails implements SerializableEntity {

    private Set<ClientData> clients = new HashSet<>();

    public Set<ClientData> getClients() {
        return clients;
    }

    public void addClient(Long certId, String serviceUuid) {
        clients.add(new ClientData().setCertId(certId).setServiceUuid(serviceUuid));
    }

    public static class ClientData implements SerializableEntity {
        private String serviceUuid;
        private Long certId;

        public ClientData setCertId(Long certId) {
            this.certId = certId;
            return this;
        }

        public ClientData setServiceUuid(String serviceUuid) {
            this.serviceUuid = serviceUuid;
            return this;
        }

        public String getServiceUuid() {
            return serviceUuid;
        }

        public Long getCertId() {
            return certId;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            if (certId != null)
                hash += certId.hashCode();
            if (serviceUuid != null)
                hash += serviceUuid.hashCode();
            if (hash != 0)
                return hash;
            return super.hashCode();
        }
    }
}
