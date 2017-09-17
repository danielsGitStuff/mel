package de.mein.android.controller;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.auth.data.MeinAuthSettings;
import de.mein.android.service.AndroidService;

/**
 * Created by xor on 2/22/17.
 */
public class InfoController extends GuiController {
    private Button btnStartStop, btnApply;
    private TextView lblStatus;
    private EditText txtPort, txtCertPort, txtName;

    public InfoController(MeinActivity activity, LinearLayout content) {
        super(activity, content, R.layout.content_general);
        btnStartStop = rootView.findViewById(R.id.btnStart);
        btnApply = rootView.findViewById(R.id.btnApply);
        lblStatus = rootView.findViewById(R.id.lblStatus);
        txtCertPort = rootView.findViewById(R.id.txtCertPort);
        txtName = rootView.findViewById(R.id.txtName);
        txtPort = rootView.findViewById(R.id.txtPort);

        // action listeners
        btnStartStop.setOnClickListener(v1 -> {
            //service
            Intent serviceIntent = new Intent(rootView.getContext(), AndroidService.class);
            ComponentName name = rootView.getContext().startService(serviceIntent);
            System.out.println("InfoController.InfoController.service.started: " + name.getClassName());
        });
        btnApply.setOnClickListener(v1 -> applyInputs());
        System.out.println("InfoController.InfoController");
    }

    private void applyInputs() {
        try {
            int port = Integer.parseInt(txtPort.getText().toString());
            int certPort = Integer.parseInt(txtCertPort.getText().toString());
            String name = txtName.getText().toString();
            assert !name.trim().isEmpty();
            MeinAuthSettings meinAuthSettings = androidService.getMeinAuthSettings();
            meinAuthSettings.setName(name).setDeliveryPort(certPort).setPort(port);
            meinAuthSettings.save();
        } catch (Exception e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG);
        }
    }


    @Override
    public String getTitle() {
        return "Info";
    }

    @Override
    public void onAndroidServiceAvailable() {
        showAll();
    }

    @Override
    public void onAndroidServiceUnbound(AndroidService androidService) {
        this.androidService = null;
    }

    private void showAll() {
        // fill values
        if (androidService != null) {
            activity.runOnUiThread(() -> {
                MeinAuthSettings meinAuthSettings = androidService.getMeinAuthSettings();
                txtPort.setText(meinAuthSettings.getPort().toString());
                txtName.setText(meinAuthSettings.getName());
                txtCertPort.setText(meinAuthSettings.getDeliveryPort().toString());
                if (androidService.isRunning()) {
                    lblStatus.setText("Running");
                    lblStatus.setBackgroundColor(Color.parseColor("#ff99cc00"));
                } else {
                    lblStatus.setText("Stopped");
                    lblStatus.setBackgroundColor(Color.parseColor("#ffcc0000"));
                }
            });
        }
    }
}
