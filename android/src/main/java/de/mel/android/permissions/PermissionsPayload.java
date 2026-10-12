package de.mel.android.permissions;

import java.util.ArrayList;
import java.util.List;

import de.mel.AndroidPermission;
import de.mel.core.serialize.SerializableEntity;
import fun.with.Lists;

public class PermissionsPayload implements SerializableEntity {
    private List<PermissionsEntry> entries = new ArrayList<>();

    public PermissionsPayload() {
    }

    public List<PermissionsEntry> getEntries() {
        return entries;
    }

    public PermissionsPayload addEntry(PermissionsEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public Lists<AndroidPermission> getAndroidPermissions() {
        return Lists.wrap(this.entries).map(p -> new AndroidPermission(p.getPermissionString(), p.getTitle(), p.getText()));
    }
}
