package de.mein.android.drive.controller;

import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import org.jdeferred.Promise;

import java.io.File;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MainActivity;
import de.mein.android.Notifier;
import de.mein.android.Tools;
import de.mein.android.controller.RemoteServiceChooserController;
import de.mein.android.drive.AndroidDriveBootloader;
import de.mein.android.file.SAFAccessor;
import de.mein.android.file.StoragesManager;
import de.mein.android.file.chooserdialog.DirectoryChooserDialog;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.data.DriveDetails;
import de.mein.drive.data.DriveSettings;
import de.mein.drive.data.DriveStrings;

/**
 * Created by xor on 2/25/17.
 */

public class RemoteDriveServiceChooserGuiController extends RemoteServiceChooserController {

    private EditText txtPath, txtMaxSize, txtMaxDays;
    private SeekBar maxSizeSeekBar;
    private Button btnPath, btnOptional;
    private float wastebinRatio = DriveSettings.DEFAULT_WASTEBIN_RATIO;
    private Long totalSpace;
    private Long availableSpace;
    private boolean optionalCollapsed = true;
    private RelativeLayout optionalContainer;
    private TextView lblPercent, lblHint;
    private boolean isEditingMaxSizeText = false;
    private int maxDays;
    private Long wastebinSize;
    // this is required for android 5+ only.
    private Uri rootTreeUri;
    private AFile rootFile;
    private RelativeLayout container;

    private void showIncompatibleState() {
        this.createServiceController.setBtnCreateTitle(R.string.incompatible);
        this.createServiceController.setBtnCreateEnabled(false);
        lblHint.setVisibility(View.VISIBLE);
        lblHint.setText(R.string.driveIncompatibleServerService);
        container.invalidate();
        createServiceController.invalidateLayout();
    }

    private void showNormalState() {
        this.createServiceController.setBtnCreateTitle(R.string.btnCreate);
        this.createServiceController.setBtnCreateEnabled(true);
        lblHint.setVisibility(View.GONE);
        container.invalidate();
        createServiceController.invalidateLayout();
    }

    @Override
    protected void onServiceSelected(Long selectedCertId, ServiceJoinServiceType selectedService) {
        if (selectedService.getAdditionalServicePayload() != null) {
            DriveDetails driveDetails = (DriveDetails) selectedService.getAdditionalServicePayload();
            if (driveDetails.usesSymLinks()) {
                showIncompatibleState();
            } else {
                showNormalState();
            }
        }
    }

    @Override
    protected void onRbServerSelected() {
        showNormalState();
    }

    public AFile getRootFile() {
        return rootFile;
    }

    public RemoteDriveServiceChooserGuiController(MeinAuthService meinAuthService, MainActivity activity, ViewGroup viewGroup) {
        super(meinAuthService, activity, viewGroup, R.layout.embedded_twice_drive);
    }

    private void launchDirChooser() {
        /**
         * found no other sophisticated way that delivers {@link File}s when choosing a storage location on android.
         * also backwards compatibility is a problem (storage access framework, SFA available in kitkat+ only).
         * other option would (probably) be to adapt all the file handling and tools and workers and so on to work with SFA.
         * this works around it.
         *
         * this relies on work done by the Archos people. they found a neat way to maneuver around SFA.
         * then start the directory chooser with the preselected storage (chooser cannot change the storage device itself)
         */

        AFile[] rootDirs = StoragesManager.getStorageFiles(Tools.getApplicationContext());// N.arr.fromCollection(paths, N.converter(AFile.class, element -> AFile.instance(AFile.instance(element))));
        Promise<AFile, Void, Void> result = DirectoryChooserDialog.showDialog(activity, rootDirs);
        result.done(chosenDir -> {
            setPath(chosenDir.getAbsolutePath());
        });
    }

    @Override
    protected void initEmbedded() {
        container = rootView.findViewById(R.id.tbl);
        wastebinRatio = DriveSettings.DEFAULT_WASTEBIN_RATIO;
        txtPath = rootView.findViewById(R.id.txtPath);
        btnPath = rootView.findViewById(R.id.btnPath);
        txtMaxDays = rootView.findViewById(R.id.txtMaxDays);
        txtMaxSize = rootView.findViewById(R.id.txtMaxSize);
        maxSizeSeekBar = rootView.findViewById(R.id.maxSizeSeekBar);
        btnOptional = rootView.findViewById(R.id.btnOptional);
        optionalContainer = rootView.findViewById(R.id.optionalContainer);
        lblPercent = rootView.findViewById(R.id.lblPercent);
        lblHint = rootView.findViewById(R.id.lblHint);
        setPath(createDrivePath());
        btnPath.setOnClickListener(view -> {
            // explain permissions to user first
            activity.askUserForPermissions(new AndroidDriveBootloader().getPermissions()
                    , permissionsGrantedListener
                    , R.string.permissionRequiredTitle
                    , R.string.permissionDriveWriteMessage
                    , () -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

//                            if (!SAFAccessor.canWriteInternal()){
//                                SAFAccessor.askForInternalRootDirectory(activity).done(result -> {
//                                    Lok.debug();
//                                });
//                                return;
//                            }

                            SAFAccessor.setupAllStorages(activity).done(result -> launchDirChooser());

//                            if (!SAFAccessor.internalIsSetup()) {
//                                SAFAccessor.askForInternalRootDirectory(activity);
//                            }
//
//                            if (!SAFAccessor.hasExternalSdCard() || SAFAccessor.canWriteExternal()) {
//                                launchDirChooser();
//                            } else {
//                                SAFAccessor.askForExternalRootDirectory(activity).done(nill -> {
//                                    launchDirChooser();
//                                }).fail(Throwable::printStackTrace);
//                            }
                        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                            launchDirChooser();
                        }
                    }
                    , result -> Notifier.toast(activity, R.string.toastDrivePermissionsRequired));
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
                    Lok.debug("initEmbedded");
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
        rootFile = AFile.instance(path);
        adjustMaxWasteBinRatio();
    }

    @Override
    protected boolean showService(ServiceJoinServiceType service) {
        return service.getType().v().equals(DriveStrings.NAME) && service.getAdditionalServicePayload() != null;
    }

    private String createDrivePath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
    }

    public boolean isValid() {
        final String path = txtPath.getText().toString();
        return AFile.instance(path).exists();
    }

    @Override
    public boolean onOkClicked() {
        if (rootFile == null || !rootFile.exists())
            return false;
        if (SAFAccessor.hasExternalSdCard() && (rootFile.getAbsolutePath().startsWith(SAFAccessor.getExternalSDPath()))
                && !SAFAccessor.canWriteExternal())
            return false;
        return true;
    }

    public int getMaxDays() {
        return maxDays;
    }

    public float getWastebinRatio() {
        return wastebinRatio;
    }

    @Override
    public int getPermissionsText() {
        return R.string.permissionDriveWriteMessage;
    }
}
