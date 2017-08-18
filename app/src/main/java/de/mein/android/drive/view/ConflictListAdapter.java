package de.mein.android.drive.view;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.mein.R;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.sql.Stage;


/**
 * Created by xor on 8/11/17.
 */

public class ConflictListAdapter extends BaseAdapter {

    private final ListView listView;
    private List<Conflict> items;
    private final Activity activity;
    private final LayoutInflater layoutInflator;
    private final int imageDirectoryId = R.drawable.ic_menu_add;
    private final int imageFileId = R.drawable.ic_menu_search;
    private Conflict upperConflict;
    private final View.OnClickListener onUpClickedListener;
    private boolean isRoot = true;

    public ConflictListAdapter(ListView listView, Activity activity, Conflict upperConflict, Collection<Conflict> conflicts) {
        this.activity = activity;
        this.listView = listView;
        this.layoutInflator = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.onUpClickedListener = view -> init(upperConflict.getDependsOn(), conflicts);
        init(upperConflict, conflicts);
    }

    private void init(Conflict upperConflict, Collection<Conflict> conflicts) {
        this.upperConflict = upperConflict;
        this.items = sort(conflicts);
        isRoot = upperConflict == null || (upperConflict != null && upperConflict.getDependsOn() != null);
    }

    private List<Conflict> sort(Collection<Conflict> dependents) {
        List<Conflict> result = new ArrayList<>();
        result.addAll(dependents);
        result.sort((c1, c2) -> {
            return compareConflicts(c1, c2);
        });
        return result;
    }

    private int compareConflicts(Conflict c1, Conflict c2) {
        Stage first, sec;
        if (c1.getLeft() != null)
            first = c1.getLeft();
        else
            first = c1.getRight();
        if (c2.getLeft() != null)
            sec = c2.getLeft();
        else
            sec = c2.getRight();
        return first.getName().compareTo(sec.getName());
    }

    @Override
    public int getCount() {
        return isRoot ? items.size() : items.size() + 1;
    }

    @Override
    public Object getItem(int i) {
        return items.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View oldeView, ViewGroup viewGroup) {
        if (i == 0 && !isRoot) {
            TextView textView = (TextView) layoutInflator.inflate(android.R.layout.simple_list_item_1, null);
            textView.setText("[go up]");
            textView.setOnClickListener(onUpClickedListener);
            return textView;
        }
        View view = layoutInflator.inflate(R.layout.listitem_conflict, null);
        TextView txtLeft = view.findViewById(R.id.txtLeft);
        TextView txtRight = view.findViewById(R.id.txtRight);
        ImageView imageLeft = view.findViewById(R.id.imageLeft);
        ImageView imageRight = view.findViewById(R.id.imageRight);
        LinearLayout layoutLeft = view.findViewById(R.id.layoutLeft);
        LinearLayout layoutRight = view.findViewById(R.id.layoutRight);

        Conflict conflict = items.get(i);
        // find reasonable captions for both sides
        Stage leftStage = conflict.getLeft();
        Stage rightStage = conflict.getRight();
        if (leftStage != null) {
            txtLeft.setText(leftStage.getName());
            if (leftStage.getIsDirectory())
                imageLeft.setImageResource(imageDirectoryId);
            else
                imageLeft.setImageResource(imageFileId);

        } else {
            txtLeft.setText("-- not available--");
        }
        if (rightStage != null) {
            txtRight.setText(rightStage.getName());
            if (rightStage.getIsDirectory())
                imageRight.setImageResource(imageDirectoryId);
            else
                imageRight.setImageResource(imageFileId);
        } else {
            txtRight.setText("-- not available --");
        }
        //setup click listener
        view.setOnClickListener(vv -> {
            Conflict subConflict = null;
            init(conflict,conflict.getDependents());
            activity.runOnUiThread(()-> ConflictListAdapter.this.notifyDataSetChanged());
//            if (leftStage != null && leftStage.getIsDirectory()) {
//                subConflict = conflict.getDependentByName(leftStage.getName());
//            }
//            if (subConflict == null && rightStage != null && rightStage.getIsDirectory()) {
//                subConflict = conflict.getDependentByName(rightStage.getName());
//            }
//            if (subConflict != null)
//                init(subConflict, subConflict.getDependents());
        });
        return view;
    }


}
