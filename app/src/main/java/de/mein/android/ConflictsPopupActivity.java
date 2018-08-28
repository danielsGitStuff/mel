package de.mein.android;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;

import de.mein.R;
import de.mein.android.service.AndroidService;
import de.mein.auth.service.IMeinService;

/**
 * Created by xor on 10/18/17.
 */

public abstract class ConflictsPopupActivity <T extends IMeinService> extends PopupActivity<T>{
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
