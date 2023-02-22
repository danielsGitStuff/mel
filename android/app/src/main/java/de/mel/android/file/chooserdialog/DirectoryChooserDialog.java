package de.mel.android.file.chooserdialog;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import de.mel.Lok;
import de.mel.R;
import de.mel.android.MelActivity;
import de.mel.android.Notifier;
import de.mel.android.PopupActivity;
import de.mel.android.filesync.data.AndroidFileSyncStrings;
import de.mel.auth.MelNotification;
import de.mel.auth.MelStrings;
import de.mel.auth.data.ServicePayload;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.file.AbstractFile;
import de.mel.auth.file.IFile;
import de.mel.auth.service.IMelService;
import de.mel.auth.socket.process.transfer.MelIsolatedProcess;
import de.mel.auth.socket.process.val.Request;
import de.mel.auth.tools.N;
import de.mel.auth.tools.NWrap;
import de.mel.core.serialize.SerializableEntity;
import de.mel.core.serialize.deserialize.entity.SerializableEntityDeserializer;
import fun.with.Lists;

public class DirectoryChooserDialog extends PopupActivity<DirectoryChooserDialog.VoidService> {
    private Button btnCancel, btnOk;
    private ImageButton btnUp;
    private RecyclerView list;
    private TextView txtPath;
    private IFile[] rootDirs;
    private IFile currentDir;
    private Stack<IFile> parentDirs = new Stack<>();
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
        FilesActivityPayload payload = N.rte(() -> (FilesActivityPayload) SerializableEntityDeserializer.deserialize(this.payloadJson));
        FileAdapter adapter = new FileAdapter(this, list);
        List<IFile> directories = Lists.wrap(payload.getPaths()).map(AbstractFile::instance).get();
        adapter.setDirectories(directories);
        adapter.setOnClicked(clickedDir -> {
            parentDirs.push(currentDir);
            currentDir = clickedDir;
            depth++;
            if (currentDir != null) {
                txtPath.setText(currentDir.getAbsolutePath());
            }
            Lok.debug("DirectoryChooserDialog.init.depth " + depth);
            IFile[] subDirs = clickedDir.listDirectories();
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
                IFile[] subDirs;
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
                result.putExtra(MelStrings.Activity.SOURCE_REQUEST_ID, requestCode);
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

    public static class FilesActivityPayload implements SerializableEntity {

        private List<String> paths;

        public FilesActivityPayload(List<String> paths) {
            this.paths = paths;
        }

        public List<String> getPaths() {
            return paths;
        }

        private FilesActivityPayload() {
        }
    }

    public static Promise<IFile, Void, Void> showDialog(MelActivity activity, IFile[] rootDirectories) {
        final NWrap.BWrap isRoot = new NWrap.BWrap(true);
        Deferred<IFile, Void, Void> deferred = new DeferredObject<>();
        Intent intent = new Intent(activity, DirectoryChooserDialog.class);
        FilesActivityPayload payload = new FilesActivityPayload(Lists.wrap(rootDirectories).map(IFile::getAbsolutePath).get());
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
