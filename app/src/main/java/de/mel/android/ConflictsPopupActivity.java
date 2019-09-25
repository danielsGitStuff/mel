package de.mel.android;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;

import de.mel.R;
import de.mel.android.service.AndroidService;
import de.mel.auth.service.IMelService;

/**
 * Created by xor on 10/18/17.
 */

public abstract class ConflictsPopupActivity <T extends IMelService> extends PopupActivity<T>{
    protected ListView listView;
    protected Button btnChooseLeft, btnChooseRight, btnOk;

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
    protected int layout() {
        return R.layout.activity_conflict_popup;
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
    }
}
