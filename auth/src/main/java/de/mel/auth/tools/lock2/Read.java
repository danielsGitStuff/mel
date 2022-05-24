package de.mel.auth.tools.lock2;

public class Read {
    private Object[] objects;

    public Read(Object... objects) {
        this.objects = objects;
    }
    public Object[] getObjects() {
        return objects;
    }
}
