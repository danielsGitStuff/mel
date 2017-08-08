package de.mein.android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import de.mein.R;
import de.mein.drive.data.DriveStrings;

public class ConflictPopupActivity extends AppCompatActivity {

    private Integer requestCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_popup);
        Bundle extra = getIntent().getExtras();
        requestCode =  extra.getInt(DriveStrings.Notifications.REQUEST_CODE);
    }


}
