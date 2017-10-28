package de.mein.contacts.data;

import de.mein.auth.data.ClientData;
import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by xor on 9/21/17.
 */

public class ContactsServerSettings implements SerializableEntity {
    private Set<ClientData> clients = new HashSet<>();
    public void addClient(Long certId, String serviceUuid) {
        clients.add(new ClientData(certId,serviceUuid));
    }

    public Set<ClientData> getClients() {
        return clients;
    }
}
