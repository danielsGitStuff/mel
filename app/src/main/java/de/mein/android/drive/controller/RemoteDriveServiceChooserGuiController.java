package de.mein.android.drive.controller;

import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.localstorage.JavaFile2;


import org.jdeferred.Promise;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.Notifier;
import de.mein.android.Tools;
import de.mein.android.controller.RemoteServiceChooserController;
import de.mein.android.drive.AndroidDriveBootloader;
import de.mein.android.file.chooserdialog.DirectoryChooserDialog;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.file.AFile;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.drive.bash.BashTools;
import de.mein.drive.bash.BashToolsUnix;
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
    // this is required for android 5+ only.
    private Uri rootTreeUri;
    private DocumentFile rootFile;

    public DocumentFile getRootFile() {
        return rootFile;
    }

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
            Promise<Void, List<String>, Void> permissionsPromise = activity.annoyWithPermissions(new AndroidDriveBootloader().getPermissions());
            permissionsPromise.done(nil -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && false) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    i.addCategory(Intent.CATEGORY_DEFAULT);
                    activity.launchActivityForResult(Intent.createChooser(i, "Choose directory"), (resultCode, resultData) -> {
                        System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                        if (resultCode == Activity.RESULT_OK) {
                            // Get Uri from Storage Access Framework.
                            rootTreeUri = resultData.getData();
                            setPath(rootTreeUri.toString());


                            // Persist URI in shared preference so that you can use it later.
                            // Use your own framework here instead of PreferenceUtil.
                            //PreferenceUtil.setSharedPreferenceUri(R.string.key_internal_uri_extsdcard, treeUri);


                            // Persist access permissions.
                            final int takeFlags = resultData.getFlags()
                                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            activity.getContentResolver().takePersistableUriPermission(rootTreeUri, takeFlags);
                            List<UriPermission> uris = activity.getContentResolver().getPersistedUriPermissions();
//                            System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                            rootFile = DocumentFile.fromTreeUri(activity, rootTreeUri);
                            String mime = rootFile.getType();
                            System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                            N.r(() -> {
                                System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                JavaFile2 ra = (JavaFile2) JavaFile2.fromUri(rootTreeUri);

                                Uri uuu = rootFile.getUri();
                                Cursor c = activity.getContentResolver().query(uuu, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, "_data"}
                                        , DocumentsContract.Document.COLUMN_DOCUMENT_ID + "=?", new String[]{DocumentsContract.getTreeDocumentId(rootTreeUri)}, null);
                                while (c.moveToNext()) {
                                    String s1 = c.getString(0);
                                    String s2 = c.getString(1);
                                    String s3 = c.getString(2);
                                    System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                }
                                System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                            });
                            N.r(() -> {
                                //debug stuff

                                ExtStorageManager extStorageManager = ExtStorageManager.getExtStorageManager();
                                List<String> sdCards = extStorageManager.getExtSdcards();
                                N.forEach(sdCards, (stoppable, index, s) -> {
                                    System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                    Uri rooturi = Uri.parse("file://" + s);
                                    AFile rootFile = AFile.instance(new File(s));
                                    BashToolsUnix bu = new BashToolsUnix();
                                    Iterator<AFile> contentIterator = bu.find(rootFile, AFile.instance(rootFile, "bla"));
                                    N.forEach(contentIterator, (stoppable1, index1, aFile) -> {
                                        System.out.println(aFile.getAbsolutePath());
                                        JavaFile2 f2 = new JavaFile2(new File(aFile.getAbsolutePath()));
                                        System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                    });
                                    System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                });
                                File[] one = RemoteDriveServiceChooserGuiController.this.activity.getApplicationContext().getExternalMediaDirs();
                                File two = Environment.getExternalStorageDirectory();
                                String state = Environment.getExternalStorageState();
                                System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                                File f = new File("/mnt/media_rw/1E03-251C");
                                if (f.exists()) {
                                    File[] b = BashTools.lsD("/mnt/media_rw");
                                    File[] files = f.listFiles();
                                    N.forEach(files, (stoppable, index, file) -> System.out.println(file.getAbsolutePath()));
                                }
                                System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded.end");
                            });
//                            AFile rootFile = AFile.instance(rootTreeUri.toString());
//                            AFile music = AFile.instance(rootFile,"Music");
//                            boolean exists = music.exists();
//                            System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
//                            AFile wrong = AFile.instance(rootFile,"doesnotexist");
//                            boolean alsoexists = wrong.exists();
//                            System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
//
//                            DocumentFile[] content = root.listFiles();
//                            N.forEach(content, (stoppable, index, documentFile1) -> {
//                                if (documentFile1.isDirectory()){
//                                    N.forEach(documentFile1.listFiles(),(stoppable1, index1, documentFile2) -> {
//                                        if (documentFile2.isDirectory())
//                                            N.forEach(documentFile2.listFiles(),(stoppable2, index2, documentFile3) -> {
//                                                Uri u = documentFile3.getUri();
//                                                String path = u.getEncodedPath();
//                                                String path2 = u.getPath();
//                                                DocumentFile d1 = DocumentFile.fromSingleUri(activity, u);
//                                                DocumentFile d2 = DocumentFile.fromTreeUri(activity, u);
//                                                Uri u2 = Uri.withAppendedPath(u, documentFile1.getName());
//                                                DocumentFile d3 = DocumentFile.fromTreeUri(activity, u2);
//                                                System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded..." + path);
//                                            });
//                                    });
//                                }
//
//                            });
//                            System.out.println("RemoteDriveServiceChooserGuiController.initEmbedded");
                        }
                    });
                } else {
                    /**
                     * found no other sophisticated way that delivers {@link File}s when choosing a storage location on android.
                     * also backwards compatibility is a problem (storage access framework, SFA available in kitkat+ only).
                     * other option would (probably) be to adapt all the file handling and tools and workers and so on to work with SFA.
                     * this works around it.
                     *
                     * this relies on work done by the Archos people. they found a neat way to maneuver around SFA.
                     * then start the directory chooser with the preselected storage (chooser cannot change the storage device itself)
                     */
                    ExtStorageManager extStorageManager = ExtStorageManager.getExtStorageManager();
                    List<String> paths = extStorageManager.getExtSdcards();
                    paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
                    AFile[] rootDirs = N.arr.fromCollection(paths,N.converter(AFile.class,element -> AFile.instance(AFile.instance(element))));
                    Promise<AFile, Void, Void> result = DirectoryChooserDialog.showDialog(activity,rootDirs);
                    result.done(result1 -> setPath(result1.getAbsolutePath()));
//
                }

            }).fail(result -> {
                Notifier.toast(activity, R.string.toastDrivePermissionsRequired);
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
        File debug = Environment.getExternalStorageDirectory();
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(downloadDir, "drive").getAbsolutePath();
    }

    public boolean isValid() {
        final String path = txtPath.getText().toString();
        // check if file exists for SAF and the normal way
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                DocumentFile documentFile = DocumentFile.fromTreeUri(activity, Uri.parse(path));
                return documentFile.exists();
            } catch (Exception e) {
                return false;
            }
        } else {
            File dir = new File(path);
            dir.mkdirs();
            return dir.exists();
        }

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
