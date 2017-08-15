package de.mein.android.drive.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import de.mein.R;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.sql.Stage;

/**
 * Created by xor on 8/11/17.
 */

public class ConflictListAdapter extends BaseAdapter {

    private final List<Conflict> conflicts;
    private final Context context;
    private final LayoutInflater layoutInflator;
    private final int imageDirectoryId = R.drawable.ic_menu_add;
    private final int imageFileId = R.drawable.ic_menu_search;

    public ConflictListAdapter(Context context, List<Conflict> conflicts) {
        this.context = context;
        this.conflicts = conflicts;
        this.layoutInflator = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    }

    @Override
    public int getCount() {
        return conflicts.size();
    }

    @Override
    public Object getItem(int i) {
        return conflicts.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null)
            view = layoutInflator.inflate(R.layout.listitem_conflict, null);
        TextView txtLeft = view.findViewById(R.id.txtLeft);
        TextView txtRight = view.findViewById(R.id.txtRight);
        ImageView imageLeft = view.findViewById(R.id.imageLeft);
        ImageView imageRight = view.findViewById(R.id.imageRight);

        Conflict conflict = conflicts.get(i);
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
        return view;
    }
}
