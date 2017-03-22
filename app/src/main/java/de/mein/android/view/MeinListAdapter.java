package de.mein.android.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mein.de.meindrive.R;

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

    public static View create2LineView(MeinListAdapter adapter, String line1, String line2) {
        View v = adapter.layoutInflator.inflate(R.layout.line_2_list_item, null);
        TextView lbl1 = (TextView) v.findViewById(R.id.lbl1);
        lbl1.setText(line1);
        TextView lbl2 = (TextView) v.findViewById(R.id.lbl2);
        lbl2.setText(line2);
        return v;
    }
}
