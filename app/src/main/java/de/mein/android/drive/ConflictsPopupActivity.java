package de.mein.android.drive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.List;
import java.util.Map;

import de.mein.R;
import de.mein.android.PopupActivity;
import de.mein.android.drive.view.ConflictListAdapter;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.service.MeinDriveClientService;

/**
 * Created by xor on 07.08.2017.
 */

public class ConflictsPopupActivity extends PopupActivity<MeinDriveClientService> {
    private Map<String, ConflictSolver> conflictSolverMap;
    private ListView listView;
    private ListAdapter listAdapter;

    @Override
    protected void onServiceConnected() {
        conflictSolverMap = service.getConflictSolverMap();
        listView = findViewById(R.id.listView);
        for (ConflictSolver conflictSolver : conflictSolverMap.values()) {
            runner.runTry(() -> {
                if (conflictSolver.hasConflicts() && conflictSolver.isSolved()) {
                    List<Conflict> conflicts = Conflict.prepareConflicts(conflictSolver.getConflicts());
                    listAdapter = new ConflictListAdapter(getApplicationContext(),conflicts);
                    listView.setAdapter(listAdapter);
                }
            });

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
