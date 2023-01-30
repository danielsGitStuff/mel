package de.mel.android.permissions;

import de.mel.core.serialize.SerializableEntity;

public class PermissionsEntry implements SerializableEntity {
    private Integer title, text;

    private String permissionString;

    public PermissionsEntry(){}

    public Integer getTitle() {
        return title;
    }

    public PermissionsEntry setTitle(Integer title) {
        this.title = title;
        return this;
    }

    public Integer getText() {
        return text;
    }

    public PermissionsEntry setText(Integer text) {
        this.text = text;
        return this;
    }

    public String getPermissionString() {
        return permissionString;
    }

    public PermissionsEntry setPermissionString(String permissionString) {
        this.permissionString = permissionString;
        return this;
    }
}
