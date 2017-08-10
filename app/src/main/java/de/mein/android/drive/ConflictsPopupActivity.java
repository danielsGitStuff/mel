package de.mein.android.drive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

import java.util.Map;

import de.mein.R;
import de.mein.android.PopupActivity;
import de.mein.drive.data.conflict.ConflictSolver;
import de.mein.drive.service.MeinDriveClientService;

/**
 * Created by xor on 07.08.2017.
 */

public class ConflictsPopupActivity extends PopupActivity<MeinDriveClientService> {
    private Map<String, ConflictSolver> conflictSolverMap;
    private ListView listView;

    @Override
    protected void onServiceConnected() {
        conflictSolverMap = service.getConflictSolverMap();
        listView = findViewById(R.id.listView);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
