package de.mel.android.drive;

import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.MenuItem;

import java.util.List;
import java.util.Map;

import de.mel.R;
import de.mel.android.ConflictsPopupActivity;
import de.mel.android.Notifier;
import de.mel.android.drive.view.FileSyncConflictListAdapter;
import de.mel.android.service.AndroidService;
import de.mel.filesync.data.conflict.Conflict;
import de.mel.filesync.data.conflict.ConflictSolver;
import de.mel.filesync.jobs.CommitJob;
import de.mel.filesync.service.MelFileSyncClientService;

/**
 * Created by xor on 07.08.2017.
 */

public class FileSyncConflictsPopupActivity extends ConflictsPopupActivity<MelFileSyncClientService> {
    private Map<String, ConflictSolver> conflictSolverMap;
    private FileSyncConflictListAdapter listAdapter;
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
                listAdapter = new FileSyncConflictListAdapter(listView, this, conflicts);
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
