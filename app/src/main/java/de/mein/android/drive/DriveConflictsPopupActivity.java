package de.mein.android.drive;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import java.util.List;
import java.util.Map;

import de.mein.R;
import de.mein.android.ConflictsPopupActivity;
import de.mein.android.Notifier;
import de.mein.android.drive.view.DriveConflictListAdapter;
import de.mein.android.service.AndroidService;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;

/**
 * Created by xor on 07.08.2017.
 */

public class DriveConflictsPopupActivity extends ConflictsPopupActivity<MeinDriveClientService> {
    private Map<String, ConflictSolver> conflictSolverMap;
    private DriveConflictListAdapter listAdapter;
    private ConflictSolver conflictSolver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        conflictSolverMap = service.getConflictSolverMap();
        listView = findViewById(R.id.listView);
        //search for the first not solved ConflictSolver. There still might be solved ones that do not need our attention here.
        for (ConflictSolver conflictSolver : conflictSolverMap.values()) {
            if (conflictSolver.hasConflicts() && !conflictSolver.isSolved()) {
                this.conflictSolver = conflictSolver;
                List<Conflict> conflicts = Conflict.getRootConflicts(conflictSolver.getConflicts());
                listAdapter = new DriveConflictListAdapter(listView, this, conflicts);
                runOnUiThread(() -> {
                    listView.setAdapter(listAdapter);
                });
                //stop after the first one. we can only show one Activity anyway.
                break;
            }
        }
        btnOk.setOnClickListener(view -> {
            if (conflictSolver.isSolved()) {
                Notifier.cancel( getIntent(), requestCode);
                service.addJob(new CommitJob());
                finish();
            } else {
                Notifier.toast(this, "not all conflicts were resolved");
            }
        });
        btnChooseLeft.setOnClickListener(view -> {
            if (conflictSolver != null) {
                for (Conflict conflict : conflictSolver.getConflicts()) {
                    if (!conflict.isLeft())
                        conflict.chooseLeft();
                }
                listAdapter.notifyDataSetChanged();
            }
        });
        btnChooseRight.setOnClickListener(view -> {
            if (conflictSolver != null) {
                for (Conflict conflict : conflictSolver.getConflicts()) {
                    if (!conflict.isRight())
                        conflict.chooseRight();
                }
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

}
