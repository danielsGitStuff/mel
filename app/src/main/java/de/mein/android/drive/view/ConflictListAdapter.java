package de.mein.android.drive.view;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.List;

import de.mein.drive.data.conflict.Conflict;

/**
 * Created by xor on 8/11/17.
 */

public class ConflictListAdapter extends BaseAdapter {

    private final List<Conflict> conflicts;
    private final Context context;

    public ConflictListAdapter(Context context,List<Conflict> conflicts) {
        this.context = context;
        this.conflicts = conflicts;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        return null;
    }
}
