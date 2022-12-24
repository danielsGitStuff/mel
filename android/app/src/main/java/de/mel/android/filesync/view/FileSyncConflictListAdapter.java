package de.mel.android.filesync.view;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

import de.mel.R;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.sql.Stage;


/**
 * Created by xor on 8/11/17.
 */

public class FileSyncConflictListAdapter extends BaseAdapter {

    private final ListView listView;
    private final Collection<Conflict> rootConflicts;
    private List<Conflict> items;
    private final AppCompatActivity activity;
    private final LayoutInflater layoutInflator;
    private final int imageDirectoryId = R.drawable.ic_menu_add;
    private final int imageFileId = R.drawable.ic_menu_search;
    private Conflict upperConflict;
    private final View.OnClickListener onUpClickedListener;
    private boolean isRoot = true;
    private int lastCount;
    private final int red = Color.argb(120, 125, 0, 0);
    private final int green = Color.argb(120, 0, 120, 0);

    public FileSyncConflictListAdapter(ListView listView, AppCompatActivity activity, Collection<Conflict> rootConflicts) {
        this.activity = activity;
        this.listView = listView;
        this.rootConflicts = rootConflicts;
        this.layoutInflator = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.onUpClickedListener = view -> {
            if (FileSyncConflictListAdapter.this.upperConflict != null) {
                Collection<Conflict> conflicts = null;
                if (FileSyncConflictListAdapter.this.upperConflict.hasParent())
                    conflicts = FileSyncConflictListAdapter.this.upperConflict.getParent().getChildren();
                if (conflicts == null)
                    conflicts = rootConflicts;
                init(FileSyncConflictListAdapter.this.upperConflict.getParent(), conflicts);
            } else {
                init(null, rootConflicts);
            }
            FileSyncConflictListAdapter.this.notifyDataSetChanged();
        };
        init(null, rootConflicts);
    }

    private void init(Conflict upperConflict, Collection<Conflict> conflicts) {
        this.upperConflict = upperConflict;
        this.items = sort(conflicts);
        isRoot = upperConflict == null || (upperConflict != null && upperConflict.hasParent());
    }

    private List<Conflict> sort(Collection<Conflict> dependents) {
        List<Conflict> result = new ArrayList<>();
        result.addAll(dependents);
        Collections.sort(result, (o1, o2) -> compareConflicts(o1, o2));
//        result.sort((c1, c2) -> {
//            return compareConflicts(c1, c2);
//        });
        return result;
    }

    private int compareConflicts(Conflict c1, Conflict c2) {
        Stage first, sec;
        if (c1.hasLocalStage())
            first = c1.getLocalStage();
        else
            first = c1.getRemoteStage();
        if (c2.hasLocalStage())
            sec = c2.getLocalStage();
        else
            sec = c2.getRemoteStage();
        return first.getName().compareTo(sec.getName());
    }

    @Override
    public int getCount() {
        lastCount = isRoot ? items.size() : items.size() + 1;
        return lastCount;
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
        if (!isRoot)
            i--;
        View view = layoutInflator.inflate(R.layout.listitem_drive_conflict, null);
        TextView txtLeft = view.findViewById(R.id.txtLeft);
        TextView txtRight = view.findViewById(R.id.txtRight);
        TextView txtAddLeft = view.findViewById(R.id.txtAdditionalLeft);
        TextView txtAddRight = view.findViewById(R.id.txtAdditionalRight);
        ImageView imageLeft = view.findViewById(R.id.imageLeft);
        ImageView imageRight = view.findViewById(R.id.imageRight);
        LinearLayout layoutLeft = view.findViewById(R.id.layoutLeft);
        LinearLayout layoutRight = view.findViewById(R.id.layoutRight);
        RadioButton rdLeft = view.findViewById(R.id.rdLeft);
        RadioButton rdRight = view.findViewById(R.id.rdRight);

        Conflict conflict = items.get(i);
        // find reasonable captions for both sides
        Stage leftStage = conflict.getLocalStage();
        Stage rightStage = conflict.getRemoteStage();
        if (leftStage != null) {
            txtLeft.setText(leftStage.getName());
            if (leftStage.getIsDirectory())
                imageLeft.setImageResource(imageDirectoryId);
            else
                imageLeft.setImageResource(imageFileId);
            if (leftStage.getDeleted())
                txtAddLeft.setText("---deleted---");
            else
                txtAddLeft.setText(leftStage.getContentHash());

        } else {
            txtLeft.setText("-- not available--");
        }
        if (rightStage != null) {
            txtRight.setText(rightStage.getName());
            if (rightStage.getIsDirectory())
                imageRight.setImageResource(imageDirectoryId);
            else
                imageRight.setImageResource(imageFileId);
            txtAddRight.setText(rightStage.getContentHash());
        } else {
            txtRight.setText("-- not available --");
        }
        //setup click listener
        view.setOnClickListener(vv -> {
            if (conflict.hasChildren()) {
                init(conflict, conflict.getChildren());
                FileSyncConflictListAdapter.this.notifyDataSetChanged();
            }
        });
        rdLeft.setOnClickListener(vv -> {
            if (rdLeft.isChecked()) {
                conflict.decideLocal();
            }
            adjustToConflict(conflict, layoutLeft, layoutRight, rdLeft, rdRight);
        });
        rdRight.setOnClickListener(vv -> {
            if (rdRight.isChecked()) {
                conflict.decideRemote();
            }
            adjustToConflict(conflict, layoutLeft, layoutRight, rdLeft, rdRight);
        });
        adjustToConflict(conflict, layoutLeft, layoutRight, rdLeft, rdRight);
        return view;
    }

    private void adjustToConflict(Conflict conflict, LinearLayout layoutLeft, LinearLayout layoutRight, RadioButton rdLeft, RadioButton rdRight) {
        if (conflict.getChosenLocal()) {
            layoutLeft.setBackgroundColor(green);
            layoutRight.setBackgroundColor(red);
            rdLeft.setChecked(true);
            rdRight.setChecked(false);
        } else if (conflict.getChosenRemote()) {
            layoutLeft.setBackgroundColor(red);
            layoutRight.setBackgroundColor(green);
            rdLeft.setChecked(false);
            rdRight.setChecked(true);
        }
    }


}
