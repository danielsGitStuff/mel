package de.mein.auth.tools;

public class NWrap<V> {
    private V v;

    public NWrap(V v) {
        this.v = v;
    }

    public V v() {
        return v;
    }

    public NWrap v(V v) {
        this.v = v;
        return this;
    }

    @Override
    public String toString() {
        return v == null ? "null" : v.toString();
    }

    public static class BWrap extends NWrap<Boolean> {

        public BWrap(Boolean aBoolean) {
            super(aBoolean);
        }

        public void toFalse() {
            v(false);
        }

        public void toTrue() {
            v(true);
        }

        public boolean isTrue() {
            return v();
        }
    }

    public static class IWrap extends NWrap<Integer> {
        public IWrap(Integer integer) {
            super(integer);
        }

        public void inc() {
            v(v() + 1);
        }
    }

    public static class SWrap extends NWrap<String> {
        public SWrap(String string) {
            super(string);
        }
    }

    public static class DWrap extends NWrap<Double> {
        public DWrap(Double d) {
            super(d);
        }

        public void add(Double d) {
            v(v() + d);
        }
    }
}
