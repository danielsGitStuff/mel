package de.mel.auth.tools.lock;

public abstract class LockObjectContainer {
    public LockObjectContainer(Object... objects) {
        this.objects = objects;
    }

    private Object[] objects;

    public Object[] getObjects() {
        return objects;
    }
}
