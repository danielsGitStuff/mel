package de.mein.drive.controller;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import de.mein.auth.data.MeinAuthSettings;
import de.mein.auth.service.MeinAuthService;
import de.mein.drive.AndroidService;
import mein.de.meindrive.R;

/**
 * Created by xor on 2/22/17.
 */
public class GeneralController implements GuiController {
    private final View view;
    private AndroidService androidService;
    private Button btnStartStop, btnApply;
    private TextView lblStatus;
    private EditText txtPort, txtCertPort, txtName;

    public GeneralController(@NonNull View v, @Nullable AndroidService androidService) {
        this.view = v;
        this.androidService = androidService;
        btnStartStop = (Button) v.findViewById(R.id.btnStart);
        btnApply = (Button) v.findViewById(R.id.btnApply);
        lblStatus = (TextView) v.findViewById(R.id.lblStatus);
        txtCertPort = (EditText) v.findViewById(R.id.txtCertPort);
        txtName = (EditText) v.findViewById(R.id.txtName);
        txtPort = (EditText) v.findViewById(R.id.txtPort);

        // action listeners
        btnStartStop.setOnClickListener(v1 -> {
            //service
            Intent serviceIntent = new Intent(v.getContext(), AndroidService.class);
            ComponentName name = v.getContext().startService(serviceIntent);
            System.out.println("GeneralController.GeneralController.service.started: " + name.getClassName());
        });
        btnApply.setOnClickListener(v1 -> applyInputs());
        showAll();
        System.out.println("GeneralController.GeneralController");
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
            Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onMeinAuthStarted(MeinAuthService androidService) {
        showAll();
    }

    @Override
    public void onAndroidServiceBound(AndroidService androidService) {
        this.androidService = androidService;
        showAll();
    }

    private void showAll() {
        // fill values
        if (androidService != null) {
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
        }
    }
}
