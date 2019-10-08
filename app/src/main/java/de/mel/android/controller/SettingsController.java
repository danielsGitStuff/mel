package de.mel.android.controller;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;
import androidx.core.content.FileProvider;
import de.mel.BuildConfig;
import de.mel.Lok;
import de.mel.R;
import de.mel.Versioner;
import de.mel.android.*;
import de.mel.android.service.AndroidPowerManager;
import de.mel.android.service.AndroidService;
import de.mel.android.service.CopyService;
import de.mel.android.view.PowerView;
import de.mel.auth.data.MelAuthSettings;
import de.mel.auth.tools.N;
import de.mel.update.UpdateHandler;
import de.mel.update.Updater;
import de.mel.update.VersionAnswer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Created by xor on 9/19/17.
 */

public class SettingsController extends GuiController {
    private AndroidPowerManager powerManager;
    private Button btnStartStop, btnApply, btnShow, btnPowerMobile, btnPowerServer, btnAbout, btnUpdate, btnExportLok, btnExportDBs;
    private EditText txtPort, txtCertPort, txtName;
    private CheckBox cbShowFirstStartDialog, cbRedirectSysOut, cbLokToDb;
    private PowerView powerView;

    public SettingsController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_settings);
        txtCertPort = rootView.findViewById(R.id.txtCertPort);
        txtName = rootView.findViewById(R.id.txtName);
        txtPort = rootView.findViewById(R.id.txtPort);
        cbShowFirstStartDialog = rootView.findViewById(R.id.cbShowFirstStartDialog);
        cbRedirectSysOut = rootView.findViewById(R.id.cbRedirectSysOut);
        cbLokToDb = rootView.findViewById(R.id.cbLokToDb);
        btnShow = rootView.findViewById(R.id.btnShow);
        btnExportLok = rootView.findViewById(R.id.btnExportLok);
        // action listeners
        btnStartStop = rootView.findViewById(R.id.btnStart);
        btnApply = rootView.findViewById(R.id.btnApply);
        btnPowerMobile = rootView.findViewById(R.id.btnPowerMobile);
        btnPowerServer = rootView.findViewById(R.id.btnPowerServer);
        btnUpdate = rootView.findViewById(R.id.btnUpdate);
        powerView = rootView.findViewById(R.id.powerView);
        btnAbout = rootView.findViewById(R.id.btnAbout);
        btnExportDBs = rootView.findViewById(R.id.btnExportDBs);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        btnStartStop.setOnClickListener(v1 -> {
            //service
            Intent serviceIntent = new Intent(rootView.getContext(), AndroidService.class);
            ComponentName name = rootView.getContext().startService(serviceIntent);
            Lok.debug("InfoController.InfoController.service.started: " + name.getClassName());
        });
        btnShow.setOnClickListener(v -> {
            activity.showIntroGui();
        });
        btnApply.setOnClickListener(v1 -> applyInputs());
        cbShowFirstStartDialog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(activity.getString(R.string.showIntro), cbShowFirstStartDialog.isChecked());
            editor.apply();
        });
        btnPowerServer.setOnClickListener(view -> {
            powerManager.configure(true, true, true, true);
            powerView.update();
        });
        btnPowerMobile.setOnClickListener(view -> {
            powerManager.configure(true, false, false, false);
            powerView.update();
        });
        btnAbout.setOnClickListener(v -> N.r(() -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(activity);
            String variant = activity.getString(R.string.variant);
            Date versionDate = new Date(Versioner.getTimestamp());
            String text = activity.getString(R.string.variant);
            text += ": " + Versioner.getBuildVariant() + "\n"
                    + activity.getString(R.string.timestamp) + ": " + Versioner.getTimestamp().toString() + "\n"
                    + activity.getString(R.string.version) + ": " + Versioner.getVersion();
            builder.setMessage(text)
                    .setTitle(R.string.titleAbout)
                    .setPositiveButton(R.string.btnOk, null);
            WebView webView = new WebView(activity);
            WebSettings settings = webView.getSettings();
            boolean dom = settings.getDomStorageEnabled();
            boolean js = settings.getJavaScriptEnabled();
            settings.setDomStorageEnabled(true);
            settings.setJavaScriptEnabled(true);
            webView.loadUrl("file:///android_asset/de/mel/auth/licences.html");
            ScrollView scrollView = new ScrollView(activity);
            scrollView.addView(webView);
            builder.setView(scrollView);
            builder.setNegativeButton("Close", (dialog, id) -> dialog.dismiss());
            androidx.appcompat.app.AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }));
        btnUpdate.setOnClickListener(v -> N.r(() -> {
            if (activity.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Threadder.runNoTryThread(() -> {
                    if (updateHandler == null)
                        updateHandler = new UpdateHandler() {
                            private String titleReceiving;
                            private int requestCode = 732458;
                            private String titleAvail;


                            private Uri getFileUri(File file) {
                                return FileProvider.getUriForFile(Tools.getApplicationContext(),
                                        Tools.getApplicationContext().getPackageName() + ".HelperClasses.GenericFileProvider"
                                        , file);
                            }

                            @Override
                            public void onUpdateFileReceived(Updater updater, VersionAnswer.VersionEntry versionEntry, File target) {
                                Notifier.cancel(null, requestCode);

                                File toInstall = target;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    Uri apkUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", toInstall);
                                    Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                    intent.setData(apkUri);
                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    activity.startActivity(intent);
                                } else {
                                    Uri apkUri = Uri.fromFile(toInstall);
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.startActivity(intent);
                                }
                                updater.removeUpdateHandler(this);
                            }

                            @Override
                            public void onProgress(Updater updater, Long done, Long length) {
                                int p = 0;
                                if (done > 0) {
                                    p = (int) ((float) done / (float) length * 100f);
                                }
                                Notifier.progress(requestCode, R.drawable.icon_notification, Notifier.CHANNEL_ID_SILENT, titleReceiving, null, null, 100, p);
                            }

                            @Override
                            public void onUpdateAvailable(Updater updater, VersionAnswer.VersionEntry versionEntry) {
                                titleAvail = androidService.getString(R.string.titleUpdateAvail);
                                titleReceiving = androidService.getString(R.string.titleReceiving);
                                activity.showMessageBinary(R.string.titleUpdateAvail, R.string.questionDownloadUpdate, (dialog, which) -> {
                                    File parent = Environment.getExternalStoragePublicDirectory("Download");
                                    File updateFile = new File(parent, "update.apk");
                                    Threadder.runNoTryThread(() -> updater.loadUpdate(versionEntry, updateFile));
                                }, (dialog, which) -> {
                                    dialog.cancel();
                                    updater.removeUpdateHandler(updateHandler);
                                });
//                                Notifier.notification(requestCode, R.drawable.icon_notification_2, Notifier.CHANNEL_ID_SILENT, titleAvail, "load?", null);
//                                File parent = Environment.getExternalStoragePublicDirectory("Download");
//                                File updateFile = new File(parent, "update.apk");
//                                updater.loadUpdate(versionEntry, updateFile);
                            }

                            @Override
                            public void onNoUpdateAvailable(Updater updater) {
                                activity.showMessage(R.string.infoNoUpdate, R.string.infoNoUpdate);
                                updater.removeUpdateHandler(updateHandler);
                            }
                        };
                    Updater updater = androidService.getMelAuthService().getUpdater().clearUpdateHandlers().addUpdateHandler(updateHandler);
                    updater.retrieveUpdate();

                });
            } else {
                Notifier.toast(activity, R.string.permanentNotification);
                activity.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }

        }));
        cbLokToDb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Long preserveLogLines = cbLokToDb.isChecked() ? 2000L : 0L;
            prefs.edit().putLong(PreferenceStrings.LOK_PRESERVED_LINES, preserveLogLines).apply();
            AndroidLok.setupDbLok(Tools.getApplicationContext());
        });
        btnExportLok.setOnClickListener(v -> N.r(() -> {
            if (!activity.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                activity.askUserForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}
                        , null
                        , R.string.settingsPermissionTitle
                        , R.string.settingsPermissionText
                        , () -> N.r(this::exportLok)
                        , result -> Notifier.toast(activity, R.string.settingsPermToastFail));
            } else {
                exportLok();
            }
        }));
        btnExportDBs.setOnClickListener(view -> {
//            if (!activity.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            activity.askUserForPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    , () -> {
                        Lok.debug("listener called");
                    }, R.string.permissionRequiredTitle,
                    R.string.permissionDriveWriteMessage
                    , () -> {
                        Lok.debug("success");
                        N.r(this::exportDBs);
                    }, result -> Lok.debug("failed"));
//            }
        });
    }

    private void exportLok() throws IOException {
        File dbFile = Tools.getApplicationContext().getDatabasePath("log");
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File targetFile = new File(downloadDir, "mel.log.db");
        CopyService.copyStream(new FileInputStream(dbFile), new FileOutputStream(targetFile));
        String msg = activity.getString(R.string.settingsLokExportToast) + targetFile.getAbsolutePath();
        Notifier.toast(activity, msg);
    }

    private void exportDBs() throws IOException {
        File dbFolder = Tools.getApplicationContext().getDatabasePath("log").getParentFile();
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        N.forEach(dbFolder.listFiles(), file -> {
            String exportName = file.getName();
            if (!exportName.endsWith(".db"))
                exportName += ".db";
            File target = new File(downloadDir, exportName);
            CopyService.copyStream(new FileInputStream(file), new FileOutputStream(target));
        });
        Notifier.toast(activity, "databases exported to download folder");
    }

    private UpdateHandler updateHandler;

    private void showAll() {
        // fill values
        if (androidService != null) {
            activity.runOnUiThread(() -> {
                MelAuthSettings melAuthSettings = androidService.getMelAuthSettings();
                txtPort.setText(melAuthSettings.getPort().toString());
                txtName.setText(melAuthSettings.getName());
                txtCertPort.setText(melAuthSettings.getDeliveryPort().toString());
                cbRedirectSysOut.setChecked(Lok.isLineStorageActive());
                cbLokToDb.setChecked(Tools.getSharedPreferences().getLong(PreferenceStrings.LOK_PRESERVED_LINES, 0L) > 0L);
                cbRedirectSysOut.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    melAuthSettings.setRedirectSysout(isChecked);
                    int lines = isChecked ? 200 : 0;
                    Lok.setupSaveLok(lines, true);
                    Tools.getSharedPreferences().edit()
                            .putInt(PreferenceStrings.LOK_LINE_COUNT, lines)
                            .putBoolean(PreferenceStrings.LOK_TIMESTAMP, true)
                            .apply();
                    N.r(() -> melAuthSettings.save());
                });
            });
        }
    }

    private void applyInputs() {
        try {
            int port = Integer.parseInt(txtPort.getText().toString());
            int certPort = Integer.parseInt(txtCertPort.getText().toString());
            String name = txtName.getText().toString();
            if (name.trim().isEmpty())
                throw new Exception("No name entered");
            MelAuthSettings melAuthSettings = androidService.getMelAuthSettings();
            melAuthSettings.setName(name).setDeliveryPort(certPort).setPort(port);
            melAuthSettings.save();
        } catch (Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public Integer getTitle() {
        return R.string.settingsTitle;
    }

    @Override
    public void onAndroidServiceAvailable(AndroidService androidService) {
        super.onAndroidServiceAvailable(androidService);
        this.powerManager = (AndroidPowerManager) androidService.getMelAuthService().getPowerManager();
        CheckBox cbWorkWhenPlugged = rootView.findViewById(R.id.cbWorkWhenPlugged);
        cbWorkWhenPlugged.setChecked(powerManager.getHeavyWorkWhenPlugged());
        cbWorkWhenPlugged.setOnCheckedChangeListener((compoundButton, isChecked) -> powerManager.setHeavyWorkWhenPlugged(isChecked));
        CheckBox cbWorkWhenOffline = rootView.findViewById(R.id.cbWorkWhenOffline);
        cbWorkWhenOffline.setOnCheckedChangeListener((compoundButton, isChecked) -> powerManager.setHeavyWorkWhenOffline(isChecked));
        cbWorkWhenOffline.setChecked(powerManager.getHeavyWorkWhenOffline());
        powerView.setPowerManager(powerManager);
        powerView.update();
        showAll();
    }

    @Override
    public void onAndroidServiceUnbound() {

    }

    @Override
    public Integer getHelp() {
        return R.string.settingsHelp;
    }

    @Override
    public void onDestroy() {
        powerManager = null;
    }
}
