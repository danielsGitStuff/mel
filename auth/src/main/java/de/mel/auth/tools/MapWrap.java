package de.mel.auth.tools;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A Wrapper for a Map. You chose what it is backed by, if backed by any map at all.
 * Calling new {@link #MapWrap(Map map) MapWrap}(new {@link HashMap}()) creates an instance backed by a HashMap.
 * Calling new {@link #MapWrap(Map map) MapWrap}(null) creates an instance that is not backed by anything and will behave like a black hole:<br>
 * <ul>
 *     <li>Everything put in is lost</li>
 *     <li>Size is 0</li>
 *     <li>It does not contain anything</li>
 * </ul>
 * You can use it to avoid nasty null checks for Maps. Just use this implementation and back it by something or not.
 *
 * @param <K>
 * @param <V>
 */
public class MapWrap<K, V> implements Map<K, V> {
    private final Map<K, V> m;

    public MapWrap(Map<K, V> map) {
        this.m = map;
    }

    @Override
    public boolean containsKey(Object o) {
        return m != null && m.containsKey(o);
    }

    @Override
    public int size() {
        if (m != null)
            return m.size();
        return 0;
    }

    @Override
    public boolean isEmpty() {
        if (m != null)
            return m.isEmpty();
        return true;
    }


    @Override
    public boolean containsValue(Object o) {
        if (m != null)
            return m.containsValue(o);
        return false;
    }

    @Override
    public V put(K k, V v) {
        if (m != null)
            return m.put(k, v);
        return v;
    }

    @Override
    public V remove(Object o) {
        if (m != null)
            return m.remove(o);
        return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> map) {
        if (m != null)
            m.putAll(map);
    }

    @Override
    public void clear() {
        if (m != null)
            m.clear();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        if (m != null)
            return m.keySet();
        return new HashSet<>();
    }

    @Override
    public V get(Object k) {
        if (m != null)
            return m.get(k);
        return null;
    }

    @NotNull
    @Override
    public Collection<V> values() {
        if (m != null)
            return m.values();
        return new ArrayList<>();
    }

    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        if (m != null)
            return m.entrySet();
        return new HashSet<>();
    }
}
