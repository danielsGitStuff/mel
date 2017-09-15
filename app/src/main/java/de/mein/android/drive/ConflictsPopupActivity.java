package de.mein.android.drive;

import android.app.Activity;
import android.app.PendingIntent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.mein.R;
import de.mein.android.MeinToast;
import de.mein.android.Notifier;
import de.mein.android.PopupActivity;
import de.mein.android.drive.view.ConflictListAdapter;
import de.mein.android.service.AndroidService;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.conflict.Conflict;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.jobs.CommitJob;
import de.mein.drive.service.MeinDriveClientService;
import de.mein.drive.sql.Stage;

/**
 * Created by xor on 07.08.2017.
 */

public class ConflictsPopupActivity extends PopupActivity<MeinDriveClientService> {
    private Map<String, ConflictSolver> conflictSolverMap;
    private ListView listView;
    private ConflictListAdapter listAdapter;
    private Button btnChooseLeft, btnChooseRight, btnOk;
    private ConflictSolver conflictSolver;



    private void debugStuff() {
        runner.runTry(() -> {
            Stage left1 = new Stage().setName("1")
                    .setId(1L)
                    .setIsDirectory(true);
            Stage right1 = new Stage().setName("1")
                    .setId(2L)
                    .setIsDirectory(true);
            Stage left1_1 = new Stage().setName("1.1")
                    .setId(3L)
                    .setIsDirectory(false)
                    .setParentId(left1.getId());
            Stage right_1_1 = new Stage().setName("1.1")
                    .setId(4L)
                    .setIsDirectory(false)
                    .setParentId(right1.getId());
            Conflict c1 = new Conflict(null, left1, right1);
            Conflict c2 = new Conflict(null, left1_1, right_1_1);
            c2.dependOn(c1);
            List<Conflict> conflicts = new ArrayList<>();
            conflicts.add(c1);
            listAdapter = new ConflictListAdapter(listView, this, conflicts);
            runOnUiThread(() -> {
                listView.setAdapter(listAdapter);
            });
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listView = findViewById(R.id.listView);
        btnChooseLeft = findViewById(R.id.btnChooseLeft);
        btnChooseRight = findViewById(R.id.btnChooseRight);
        btnOk = findViewById(R.id.btnOk);
        setTitle("Conflict detected!");
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        conflictSolverMap = service.getConflictSolverMap();
        listView = findViewById(R.id.listView);
        //search for the first not solved ConflictSolver. There still might be solved ones that do not need our attention here.
        for (ConflictSolver conflictSolver : conflictSolverMap.values()) {
            if (conflictSolver.hasConflicts() && !conflictSolver.isSolved()) {
                this.conflictSolver = conflictSolver;
                List<Conflict> conflicts = Conflict.getRootConflicts(conflictSolver.getConflicts());
                listAdapter = new ConflictListAdapter(listView, this, conflicts);
                runOnUiThread(() -> {
                    listView.setAdapter(listAdapter);
                });
                //stop after the first one. we can only show one Activity anyway.
                break;
            }
        }
        btnOk.setOnClickListener(view -> {
            if (conflictSolver.isSolved()) {
                Notifier.cancel(this, getIntent(), requestCode);
                service.addJob(new CommitJob());
                finish();
            } else {
                MeinToast.toast(this, "not all conflicts were resolved");
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
