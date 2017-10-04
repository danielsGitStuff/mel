package de.mein.android.drive.controller;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;


import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.controller.AndroidServiceCreatorGuiController;
import de.mein.android.controller.ChooseServerServiceController;
import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveStrings;
import de.mein.android.view.KnownCertListAdapter;
import de.mein.android.view.ServicesListAdapter;

/**
 * Created by xor on 2/25/17.
 */

public class AndroidDriveCreateGuiController extends ChooseServerServiceController {

    private static final int REQUEST_DIRECTORY = 987;
    private EditText txtName, txtPath;

    private Button btnPath;

    public AndroidDriveCreateGuiController(MeinAuthService meinAuthService, MeinActivity activity, ViewGroup viewGroup) {
        super(meinAuthService, activity, viewGroup, R.layout.embedded_twice_drive);
    }

    @Override
    protected void initEmbedded() {
        txtName = rootView.findViewById(R.id.txtName);
        txtPath = rootView.findViewById(R.id.txtPath);
        btnPath = rootView.findViewById(R.id.btnPath);
        txtPath.setText(createDrivePath());
        btnPath.setOnClickListener(view -> {

            final Intent chooserIntent = new Intent(activity, DirectoryChooserActivity.class);
            final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                    .newDirectoryName("drive")
                    .allowReadOnlyDirectory(true)
                    .allowNewDirectoryNameModification(true)
                    .initialDirectory(createDrivePath())
                    .build();
            chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
            activity.launchActifityForResult(chooserIntent, (resultCode, result) -> {
                //result is here
                String path = result.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR);
                txtPath.setText(path);
            });
        });
    }

    @Override
    protected boolean showService(ServiceJoinServiceType service) {
        return service.getType().v().equals(DriveStrings.NAME);
    }

    private String createDrivePath() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadDir, "drive").getAbsolutePath();
    }

    public String getName() {
        return txtName.getText().toString();
    }

    public String getPath() {
        return txtPath.getText().toString();
    }

    public boolean isValid() {
        File dir = new File(txtPath.getText().toString());
        dir.mkdirs();
        if (!(dir.exists()
                && txtName.getText().toString().trim().length() > 0))
            return false;
        return true;
    }
}
