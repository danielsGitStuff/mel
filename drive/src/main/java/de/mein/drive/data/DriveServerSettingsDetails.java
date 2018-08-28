package de.mein.drive.data;

import de.mein.auth.data.ClientData;
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
        clients.add(new ClientData(certId,serviceUuid));
    }

}
