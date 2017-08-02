package de.mein.android;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import de.mein.R;
import de.mein.drive.service.sync.conflict.Conflict;
import de.mein.drive.service.sync.conflict.EmptyRowConflict;

public class PopupActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_popup);
    }

    private List<Conflict> prepareConflicts(Collection<Conflict> conflicts) {
        List<Conflict> result = new ArrayList<>();
        List<Conflict> rootConflicts = new ArrayList<>();
        for (Conflict conflict : conflicts) {
            if (conflict.getDependsOn() == null)
                rootConflicts.add(conflict);
        }
        for (Conflict root : rootConflicts) {
            result.add(root);
            traversalAdding(result, root.getDependents());
            if (root.getDependents().size() > 0)
                result.add(new EmptyRowConflict());
        }
        return result;
    }

    private void traversalAdding(List<Conflict> result, Set<Conflict> stuffToTraverse) {
        for (Conflict conflict : stuffToTraverse) {
            result.add(conflict);
            if (conflict.getDependents().size() > 0) {
                traversalAdding(result, conflict.getDependents());
            }
        }
    }
}
