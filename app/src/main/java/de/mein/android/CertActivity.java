package de.mein.android;

import android.os.Bundle;

import androidx.annotation.NonNull;

import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.widget.TextView;

import java.security.cert.X509Certificate;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.service.AndroidService;
import de.mein.auth.data.access.CertificateManager;
import de.mein.auth.data.db.Certificate;
import de.mein.sql.Hash;
import de.mein.sql.RWLock;

public class CertActivity extends PopupActivity {
    private Button btnAccept, btnReject;
    private TextView txtRemote, txtOwn, txtRemoteHash, txtOwnHash;
    private RegBundle regBundle;
    private TabHost tabHost;
    private RWLock lock = new RWLock();
    private ProgressBar progressBar;
    private TextView txtProgress;
    private ImageView imgProgress;


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
        imgProgress = findViewById(R.id.imgProgress);
        txtOwnHash = findViewById(R.id.txtOwnHash);
        txtRemoteHash = findViewById(R.id.txtRemoteHash);
        String regUuid = getIntent().getExtras().getString(AndroidRegHandler.REGBUNDLE_CERT_HASH);
        regBundle = AndroidRegHandler.retrieveRegBundle(regUuid);
        regBundle.getAndroidRegHandler().addActivity(regBundle.getHash(), this);
        if (regBundle.isFlaggedRemoteAccepted())
            onRemoteAccepted();
        showCert(txtRemote,txtRemoteHash, regBundle.getRemoteCert());
        showCert(txtOwn, txtOwnHash,regBundle.getMyCert());
        btnAccept.setOnClickListener(
                view -> {
                    regBundle.getAndroidRegHandler().onUserAccepted(regBundle);
                    Notifier.cancel(getIntent(), requestCode);
                    finish();
                }
        );
        btnReject.setOnClickListener(
                view -> Threadder.runNoTryThread(() -> {
                    regBundle.getAndroidRegHandler().onUserRejected(regBundle);
                    Notifier.cancel(getIntent(), requestCode);
                    finish();
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
        Lok.debug("NOT:IMPLEMENTED:YET");
        lock.lockWrite();
        lock.lockWrite();
    }

    private void showCert(TextView textView, TextView txtHash, Certificate cert) {
        try {
            X509Certificate x509Certificate = CertificateManager.loadX509CertificateFromBytes(cert.getCertificate().v());
            textView.setText(x509Certificate.toString());
            String hash = Hash.sha256(x509Certificate.getEncoded());
            txtHash.setText(hash);
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
        finish();
    }

    public void onRemoteRejected() {
        finish();
    }

    public void onRemoteAccepted() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.INVISIBLE);
            imgProgress.setVisibility(View.VISIBLE);
        });
    }

    public void onLocallyAccepted() {

    }
}
