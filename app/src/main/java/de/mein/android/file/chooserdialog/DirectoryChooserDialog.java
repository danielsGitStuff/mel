package de.mein.android.file.chooserdialog;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;


import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import de.mein.Lok;
import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.MeinActivityPayload;
import de.mein.android.Notifier;
import de.mein.android.PopupActivity;
import de.mein.android.drive.data.AndroidDriveStrings;
import de.mein.auth.MeinNotification;
import de.mein.auth.data.IPayload;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.file.AFile;
import de.mein.auth.service.IMeinService;
import de.mein.auth.socket.process.transfer.MeinIsolatedProcess;
import de.mein.auth.socket.process.val.Request;
import de.mein.auth.tools.N;
import de.mein.auth.tools.NWrap;

public class DirectoryChooserDialog extends PopupActivity<DirectoryChooserDialog.VoidService> {
    private Button btnCancel, btnOk;
    private ImageButton btnUp;
    private RecyclerView list;
    private TextView txtPath;
    private AFile[] rootDirs;
    private AFile currentDir;
    private Stack<AFile> parentDirs = new Stack<>();
    private int depth = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.dirChooserTitle);
        btnCancel = findViewById(R.id.btnCancel);
        btnOk = findViewById(R.id.btnOk);
        btnUp = findViewById(R.id.btnUp);
        list = findViewById(R.id.list);
        txtPath = findViewById(R.id.txtPath);
        init();
    }

    private void init() {

        rootDirs = (AFile[]) payloads.get(0).getPayload();
        FilesActivityPayload payload = (FilesActivityPayload) payloads.get(0);
        FileAdapter adapter = new FileAdapter(this, list);
        adapter.setDirectories(payload.getPayload());
        adapter.setOnClicked(clickedDir -> {
            parentDirs.push(currentDir);
            currentDir = clickedDir;
            depth++;
            if (currentDir!= null){
                txtPath.setText(currentDir.getAbsolutePath());
            }
            Lok.debug("DirectoryChooserDialog.init.depth " + depth);
            AFile[] subDirs = clickedDir.listDirectories();
            adapter.setDirectories(subDirs);
            DirectoryChooserDialog.this.runOnUiThread(adapter::notifyDataSetChanged);
            Lok.debug("DirectoryChooserDialog.init");
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        btnUp.setOnClickListener(v -> {
            if (depth > 0) {
                depth--;
                Lok.debug("DirectoryChooserDialog.init.depth " + depth);
                currentDir = parentDirs.pop();
                AFile[] subDirs;
                if (currentDir != null) {
                    subDirs = currentDir.listDirectories();
                    txtPath.setText(currentDir.getAbsolutePath());
                } else {
                    subDirs = rootDirs;
                    txtPath.setText(R.string.invalidDirectory);
                }
                adapter.setDirectories(subDirs);
                DirectoryChooserDialog.this.runOnUiThread(adapter::notifyDataSetChanged);
            }
        });
        btnOk.setOnClickListener(v -> {
            if (currentDir != null) {
                Intent result = new Intent();
                result.putExtra(AndroidDriveStrings.DIR_CHOOSER_KEY, currentDir.getAbsolutePath());
                setResult(RESULT_OK, result);
                finish();
            } else {
                Notifier.toast(this, R.string.invalidDirectory);
            }
        });
        btnCancel.setOnClickListener(v -> finish());

        runOnUiThread(adapter::notifyDataSetChanged);
    }


    private static List<String> filesToStrings(AFile[] files, NWrap.BWrap isRoot) {
        List<String> strings = new ArrayList<>();
        if (!isRoot.v())
            strings.add("..");
        N.forEachAdv(files, (stoppable, index, aFile) -> strings.add(aFile.getName()));
        return strings;
    }

    public static class FilesActivityPayload extends MeinActivityPayload<AFile[]> {
        public FilesActivityPayload(String key, AFile[] payload) {
            super(key, payload);
        }
    }

    public static Promise<AFile, Void, Void> showDialog(MeinActivity activity, AFile[] rootDirectories) {
        final NWrap.BWrap isRoot = new NWrap.BWrap(true);
        Deferred<AFile, Void, Void> deferred = new DeferredObject<>();
        Intent intent = new Intent(activity, DirectoryChooserDialog.class);
        FilesActivityPayload payload = new FilesActivityPayload(AndroidDriveStrings.DIR_CHOOSER_KEY, rootDirectories);
        activity.launchActivityForResult(intent, (resultCode, result) -> {
            String path = result.getStringExtra(AndroidDriveStrings.DIR_CHOOSER_KEY);
            deferred.resolve(AFile.instance(path));
        }, payload);
        return deferred;
    }

    @Override
    protected int layout() {
        return R.layout.activity_dirchooser_popup;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    public class VoidService implements IMeinService {
        @Override
        public void handleRequest(Request request) throws Exception {

        }

        @Override
        public void handleMessage(IPayload payload, Certificate partnerCertificate, String intent) {

        }

        @Override
        public void connectionAuthenticated(Certificate partnerCertificate) {

        }

        @Override
        public void handleCertificateSpotted(Certificate partnerCertificate) {

        }

        @Override
        public String getUuid() {
            return null;
        }

        @Override
        public void onIsolatedConnectionEstablished(MeinIsolatedProcess isolatedProcess) {

        }

        @Override
        public void onMeinAuthIsUp() {

        }

        @Override
        public MeinNotification createSendingNotification() {
            return null;
        }

        @Override
        public void onCommunicationsDisabled() {

        }

        @Override
        public void onCommunicationsEnabled() {

        }
    }
}
