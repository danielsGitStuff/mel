package de.mein.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;

import com.annimon.stream.Stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import de.mein.auth.data.NetworkEnvironment;

/**
 * Created by xor on 3/16/17.
 */

public abstract class MeinListAdapter<T> extends BaseAdapter {
    protected final LayoutInflater layoutInflator;
    protected List<T> items = new ArrayList<>();

    public MeinListAdapter(Context context) {
        this.layoutInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int i) {
        return items.get(i);
    }

    public T getItemT(int i) {
        return items.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    public MeinListAdapter<T> clear() {
        items = new ArrayList<>();
        return this;
    }

    public MeinListAdapter<T> addAll(Collection<T> items) {
        this.items.addAll(items);
        return this;
    }

    public MeinListAdapter<T> add(T t) {
        items.add(t);
        return this;
    }


}
