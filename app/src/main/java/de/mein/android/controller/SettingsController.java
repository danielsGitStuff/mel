package de.mein.android.controller;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.Date;

import androidx.core.content.FileProvider;
import de.mein.Lok;
import de.mein.R;
import de.mein.Versioner;
import de.mein.android.MainActivity;
import de.mein.android.Notifier;
import de.mein.android.PreferenceStrings;
import de.mein.android.Threadder;
import de.mein.android.Tools;
import de.mein.android.service.AndroidPowerManager;
import de.mein.android.service.AndroidService;
import de.mein.android.view.PowerView;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.tools.N;

/**
 * Created by xor on 9/19/17.
 */

public class SettingsController extends GuiController {
    private AndroidPowerManager powerManager;
    private Button btnStartStop, btnApply, btnShow, btnPowerMobile, btnPowerServer, btnAbout, btnUpdate;
    private EditText txtPort, txtCertPort, txtName;
    private CheckBox cbShowFirstStartDialog, cbRedirectSysOut;
    private PowerView powerView;

    public SettingsController(MainActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_settings);
        txtCertPort = rootView.findViewById(R.id.txtCertPort);
        txtName = rootView.findViewById(R.id.txtName);
        txtPort = rootView.findViewById(R.id.txtPort);
        cbShowFirstStartDialog = rootView.findViewById(R.id.cbShowFirstStartDialog);
        cbRedirectSysOut = rootView.findViewById(R.id.cbRedirectSysOut);
        btnShow = rootView.findViewById(R.id.btnShow);
        // action listeners
        btnStartStop = rootView.findViewById(R.id.btnStart);
        btnApply = rootView.findViewById(R.id.btnApply);
        btnPowerMobile = rootView.findViewById(R.id.btnPowerMobile);
        btnPowerServer = rootView.findViewById(R.id.btnPowerServer);
        btnUpdate = rootView.findViewById(R.id.btnUpdate);
        powerView = rootView.findViewById(R.id.powerView);
        btnAbout = rootView.findViewById(R.id.btnAbout);
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
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
            Date versionDate = new Date(Versioner.getBuildVersion());
            String text = activity.getString(R.string.version);
            text += versionDate.toString() + "\n";
            text += variant + Versioner.getBuildVariant();
            builder.setMessage(text)
                    .setTitle(R.string.titleAbout)
                    .setPositiveButton(R.string.btnOk, null);
            WebView webView = new WebView(activity);
            WebSettings settings = webView.getSettings();
            boolean dom = settings.getDomStorageEnabled();
            boolean js = settings.getJavaScriptEnabled();
            settings.setDomStorageEnabled(true);
            settings.setJavaScriptEnabled(true);
            webView.loadUrl("file:///android_asset/de/mein/auth/licenses.html");
            ScrollView scrollView = new ScrollView(activity);
            scrollView.addView(webView);
            builder.setView(scrollView);
            builder.setNegativeButton("Close", (dialog, id) -> dialog.dismiss());
            androidx.appcompat.app.AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }));
        btnUpdate.setOnClickListener(v -> N.r(() -> {
            if (activity.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                Threadder.runNoTryThread(() -> androidService.getMeinAuthService().updateProgram());
            }else {
                Notifier.toast(activity,R.string.permanentNotificationText);
                activity.annoyWithPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }));
    }

    private void showAll() {
        // fill values
        if (androidService != null) {
            activity.runOnUiThread(() -> {
                MeinAuthSettings meinAuthSettings = androidService.getMeinAuthSettings();
                txtPort.setText(meinAuthSettings.getPort().toString());
                txtName.setText(meinAuthSettings.getName());
                txtCertPort.setText(meinAuthSettings.getDeliveryPort().toString());
                cbRedirectSysOut.setChecked(Lok.isLineStorageActive());
                cbRedirectSysOut.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    meinAuthSettings.setRedirectSysout(isChecked);
                    int lines = isChecked ? 200 : 0;
                    Lok.setupSaveLok(lines, true);
                    Tools.getSharedPreferences().edit()
                            .putInt(PreferenceStrings.LOK_LINE_COUNT, lines)
                            .putBoolean(PreferenceStrings.LOK_TIMESTAMP, true)
                            .apply();
                    N.r(() -> meinAuthSettings.save());
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
            MeinAuthSettings meinAuthSettings = androidService.getMeinAuthSettings();
            meinAuthSettings.setName(name).setDeliveryPort(certPort).setPort(port);
            meinAuthSettings.save();
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
        this.powerManager = (AndroidPowerManager) androidService.getMeinAuthService().getPowerManager();
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
