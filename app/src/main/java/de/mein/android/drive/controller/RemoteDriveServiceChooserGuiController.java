package de.mein.android.drive.controller;

import android.content.Intent;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.io.File;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.Tools;
import de.mein.android.controller.RemoteServiceChooserController;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;

/**
 * Created by xor on 2/25/17.
 */

public class RemoteDriveServiceChooserGuiController extends RemoteServiceChooserController {

    private static final int REQUEST_DIRECTORY = 987;
    private EditText txtPath, txtMaxSize, txtMaxDays;
    private SeekBar maxSizeSeekBar;
    private Button btnPath, btnOptional;
    private float wastebinRatio = DriveSettings.DEFAULT_WASTEBIN_RATIO;
    private Long totalSpace;
    private Long availableSpace;
    private boolean optionalCollapsed = true;
    private RelativeLayout optionalContainer;
    private TextView lblPercent;
    private boolean isEditingMaxSizeText = false;
    private int maxDays;
    private Long wastebinSize;


    public RemoteDriveServiceChooserGuiController(MeinAuthService meinAuthService, MeinActivity activity, ViewGroup viewGroup) {
        super(meinAuthService, activity, viewGroup, R.layout.embedded_twice_drive);
    }

    @Override
    protected void initEmbedded() {
        wastebinRatio = DriveSettings.DEFAULT_WASTEBIN_RATIO;
        txtPath = rootView.findViewById(R.id.txtPath);
        btnPath = rootView.findViewById(R.id.btnPath);
        txtMaxDays = rootView.findViewById(R.id.txtMaxDays);
        txtMaxSize = rootView.findViewById(R.id.txtMaxSize);
        maxSizeSeekBar = rootView.findViewById(R.id.maxSizeSeekBar);
        btnOptional = rootView.findViewById(R.id.btnOptional);
        optionalContainer = rootView.findViewById(R.id.optionalContainer);
        lblPercent = rootView.findViewById(R.id.lblPercent);
        setPath(createDrivePath());
        btnPath.setOnClickListener(view -> {

            final Intent chooserIntent = new Intent(activity, DirectoryChooserActivity.class);
            final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                    .newDirectoryName("drive")
                    .allowReadOnlyDirectory(false)
                    .allowNewDirectoryNameModification(true)
                    .initialDirectory(createDrivePath())
                    .build();
            chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
            activity.launchActivityForResult(chooserIntent, (resultCode, result) -> {
                //result is here
                if (result != null) {
                    String path = result.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR);
                    setPath(path);
                }
            });
        });
        maxSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    wastebinRatio = (float) progress / (float) seekBar.getMax();
                    adjustMaxWasteBinRatio();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        btnOptional.setOnClickListener(view -> {
            ViewPropertyAnimator animator;
            if (optionalCollapsed) {
                RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                relativeParams.addRule(RelativeLayout.BELOW, R.id.btnOptional);
                optionalContainer.setLayoutParams(relativeParams);
                animator = optionalContainer.animate().alpha(1f);
            } else {
                animator = optionalContainer.animate().alpha(0f);
            }
            animator.withEndAction(() -> {
                if (!optionalCollapsed) {
                    RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0);
                    optionalContainer.setLayoutParams(relativeParams);
                    System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                }
                rootView.invalidate();
                optionalCollapsed = !optionalCollapsed;
            });

        });
        TextWatcher wasteSizeChangedListener = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!isEditingMaxSizeText) {
                    String txt = editable.toString();
                    try {
                        wastebinSize = Long.parseLong(txt) * 1024 * 1024;
                        updateSeekBar();
                        adjustMaxWasteBinRatio();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        txtMaxSize.addTextChangedListener(wasteSizeChangedListener);
        txtMaxSize.setOnFocusChangeListener(hideKeyboardOnFocusLostListener);
        txtMaxDays.setOnFocusChangeListener(hideKeyboardOnFocusLostListener);
        txtPath.setOnFocusChangeListener(hideKeyboardOnFocusLostListener);
        updateSeekBar();
    }

    private void updateSeekBar() {
        wastebinRatio = (float) wastebinSize / (float) totalSpace;
        maxSizeSeekBar.setProgress((int) (wastebinRatio * (float) maxSizeSeekBar.getMax()));
    }

    private void adjustMaxWasteBinRatio() {
        wastebinSize = (long) (totalSpace * wastebinRatio);
        if (wastebinSize > availableSpace) {
            wastebinSize = (long) (availableSpace * 0.9);
            wastebinRatio = (float) wastebinSize / (float) totalSpace;
            maxSizeSeekBar.setProgress((int) (wastebinRatio * maxSizeSeekBar.getMax()));
            Notifier.shortToast(Tools.getApplicationContext(), Tools.getApplicationContext().getString(R.string.editServiceDriveToastOOS));
        }
        isEditingMaxSizeText = true;
        String percent = String.format("%.2f", wastebinRatio * 100f) + "% = ";
        lblPercent.setText(percent);
        txtMaxSize.setText(Long.toString(wastebinSize / 1024 / 1024));
        isEditingMaxSizeText = false;
    }

    private void setPath(String path) {
        txtPath.setText(path);
        File dir = new File(path);
        totalSpace = dir.getTotalSpace();
        availableSpace = dir.getUsableSpace();
        adjustMaxWasteBinRatio();
    }

    @Override
    protected boolean showService(ServiceJoinServiceType service) {
        return service.getType().v().equals(DriveStrings.NAME);
    }

    private String createDrivePath() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadDir, "drive").getAbsolutePath();
    }

    public String getPath() {
        return txtPath.getText().toString();
    }

    public boolean isValid() {
        File dir = new File(txtPath.getText().toString());
        dir.mkdirs();
        return dir.exists();
    }

    @Override
    public void onOkClicked() {

    }

    public int getMaxDays() {
        return maxDays;
    }

    public float getWastebinRatio() {
        return wastebinRatio;
    }
}
