package de.mel.android.file.chooserdialog;

import android.content.Intent;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.mel.Lok;
import de.mel.R;
import de.mel.android.MelActivity;
import de.mel.android.MelActivityPayload;
import de.mel.android.Notifier;
import de.mel.android.PopupActivity;
import de.mel.android.filesync.data.AndroidFileSyncStrings;
import de.mel.auth.MelNotification;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.service.IMelService;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.NWrap;

public class DirectoryChooserDialog extends PopupActivity<DirectoryChooserDialog.VoidService> {
    private Button btnCancel, btnOk;
    private ImageButton btnUp;
    private RecyclerView list;
    private TextView txtPath;
    private AbstractFile[] rootDirs;
    private AbstractFile currentDir;
    private Stack<AbstractFile> parentDirs = new Stack<>();
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

        rootDirs = (AbstractFile[]) payloads.get(0).getPayload();
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
            AbstractFile[] subDirs = clickedDir.listDirectories();
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
                AbstractFile[] subDirs;
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
                result.putExtra(AndroidFileSyncStrings.DIR_CHOOSER_KEY, currentDir.getAbsolutePath());
                setResult(RESULT_OK, result);
                finish();
            } else {
                Notifier.toast(this, R.string.invalidDirectory);
            }
        });
        btnCancel.setOnClickListener(v -> finish());

        runOnUiThread(adapter::notifyDataSetChanged);
    }


    private static List<String> filesToStrings(AbstractFile[] files, NWrap.BWrap isRoot) {
        List<String> strings = new ArrayList<>();
        if (!isRoot.v())
            strings.add("..");
        N.forEachAdv(files, (stoppable, index, aFile) -> strings.add(aFile.getName()));
        return strings;
    }

    public static class FilesActivityPayload extends MelActivityPayload<AbstractFile[]> {
        public FilesActivityPayload(String key, AbstractFile[] payload) {
            super(key, payload);
        }
    }

    public static Promise<AbstractFile, Void, Void> showDialog(MelActivity activity, AbstractFile[] rootDirectories) {
        final NWrap.BWrap isRoot = new NWrap.BWrap(true);
        Deferred<AbstractFile, Void, Void> deferred = new DeferredObject<>();
        Intent intent = new Intent(activity, DirectoryChooserDialog.class);
        FilesActivityPayload payload = new FilesActivityPayload(AndroidFileSyncStrings.DIR_CHOOSER_KEY, rootDirectories);
        activity.launchActivityForResult(intent, (resultCode, result) -> {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                String path = result.getStringExtra(AndroidFileSyncStrings.DIR_CHOOSER_KEY);
                deferred.resolve(AbstractFile.instance(path));
            }
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

    public class VoidService implements IMelService {

        @Override
        public void onBootLevel1Finished() {

        }

        @Override
        public void onBootLevel2Finished() {

        }

        @Override
        public void handleRequest(Request request) throws Exception {

        }

        @Override
        public void handleMessage(ServicePayload payload, Certificate partnerCertificate) {

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
        public void onIsolatedConnectionEstablished(MelIsolatedProcess isolatedProcess) {

        }

        @Override
        public void onIsolatedConnectionClosed(MelIsolatedProcess isolatedProcess) {

        }

        @Override
        public void onServiceRegistered() {

        }

        @Override
        public MelNotification createSendingNotification() {
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
