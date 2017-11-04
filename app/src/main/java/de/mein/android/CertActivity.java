package de.mein.android;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

import java.security.cert.X509Certificate;

import de.mein.R;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.sql.RWLock;

public class CertActivity extends PopupActivity {
    private Button btnAccept, btnReject;
    private TextView txtRemote, txtOwn;
    private RegBundle regBundle;
    private TabHost tabHost;
    private RWLock lock = new RWLock();
    private ProgressBar progressBar;
    private TextView txtProgress;


    @Override
    protected int layout() {
        return R.layout.activity_cert_incoming;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cert_incoming);
        txtRemote = findViewById(R.id.txtRemote);
        txtOwn = findViewById(R.id.txtOwn);
        btnAccept = findViewById(R.id.btnAccept);
        btnReject = findViewById(R.id.btnReject);
        txtProgress = findViewById(R.id.txtProgress);
        progressBar = findViewById(R.id.progress);
        String regUuid = getIntent().getExtras().getString(AndroidRegHandler.REGBUNDLE_UUID);
        regBundle = AndroidRegHandler.retrieveRegBundle(regUuid);
        regBundle.getAndroidRegHandler().addActivity(regBundle.getRemoteCert(), this);
        showCert(txtRemote, regBundle.getRemoteCert());
        showCert(txtOwn, regBundle.getMyCert());
        btnAccept.setOnClickListener(
                view -> {
                    regBundle.getAndroidRegHandler().onLocallyAccepted(regBundle.getRemoteCert());
                    AndroidRegHandler.removeRegBundle(regUuid);
                    Notifier.cancel(this, getIntent(), requestCode);
                    showWaiting();
                }
        );
        btnReject.setOnClickListener(
                view -> Threadder.runNoTryThread(() -> {
                    regBundle.getAndroidRegHandler().onUserRejected(regBundle);
                    AndroidRegHandler.removeRegBundle(regUuid);
                    Notifier.cancel(this, getIntent(), requestCode);
                    showWaiting();
                })
        );
        tabHost = (TabHost) findViewById(R.id.tabHost);
        tabHost.setup();
        //Tab 1
        TabHost.TabSpec spec = tabHost.newTabSpec("Remote Cert");
        spec.setContent(R.id.tabRemote);
        spec.setIndicator("Remote Indi");
        tabHost.addTab(spec);
        //Tab 2
        spec = tabHost.newTabSpec("Own Cert");
        spec.setContent(R.id.tabOwn);
        spec.setIndicator("Own Indi");
        tabHost.addTab(spec);
    }

    private void showWaiting() {
        System.out.println("CertActivity.showWaiting.NOT:IMPLEMENTED:YET");
        lock.lockWrite();
        lock.lockWrite();
    }

    private void showCert(TextView textView, Certificate cert) {
        try {
            X509Certificate myX509Certificate = CertificateManager.loadX509CertificateFromBytes(cert.getCertificate().v());
            textView.setText(myX509Certificate.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onRegistrationFinished() {
        lock.unlockWrite();
        finish();
    }

    @Override
    protected void onAndroidServiceAvailable(AndroidService androidService) {

    }

    @Override
    protected void onDestroy() {
        regBundle.getAndroidRegHandler().removeActivityByBundle(regBundle);
        super.onDestroy();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    public void onLocallyRejected() {
        Notifier.toast(this, getText(R.string.coupleLocalRejectToast));
        finish();
    }

    public void onRemoteRejected() {
        Notifier.toast(this, getText(R.string.coupleRemoteRejectToast));
        finish();
    }

    public void onRemoteAccepted() {
        progressBar.setProgress(progressBar.getProgress()+1);
    }

    public void onLocallyAccepted() {
        progressBar.setProgress(progressBar.getProgress()+1);
    }
}
