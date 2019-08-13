package de.mein.drive.data;

import de.mein.auth.data.ClientData;
import de.mein.auth.tools.N;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by xor on 10/26/16.
 */
public class DriveServerSettingsDetails implements SerializableEntity {

    private Set<ClientData> clients = new HashSet<>();
    private Set<Long> certIds = new HashSet<>();
    @JsonIgnore
    private Map<Long, ClientData> clientsMap = new HashMap<>();

    public Set<ClientData> getClients() {
        return clients;
    }

    public void addClient(Long certId, String serviceUuid) {
        ClientData clientData = new ClientData(certId, serviceUuid);
        clients.add(clientData);
        certIds.add(certId);
        clientsMap.put(certId, clientData);
    }

    public boolean hasClient(Long certId) {
        return certIds.contains(certId);
    }

    public DriveServerSettingsDetails init() {
        N.forEach(clients, clientData -> clientsMap.put(clientData.getCertId(), clientData));
        return this;
    }

    public ClientData getClientData(long certId) {
        return clientsMap.get(certId);
    }
}
