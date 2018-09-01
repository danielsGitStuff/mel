package de.mein.android.file.chooserdialog;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;


import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.util.ArrayList;
import java.util.List;

import de.mein.R;
import de.mein.android.MeinActivity;
import de.mein.android.MeinActivityPayload;
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
    private ArrayList<AFile> rootDirs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.dirChooserTitle);
        btnCancel = findViewById(R.id.btnCancel);
        btnOk = findViewById(R.id.btnOk);
        btnUp = findViewById(R.id.btnUp);
        list = findViewById(R.id.list);
        init();
    }

    private void init() {

//        List<String> strings = filesToStrings(rootDirectories, isRoot);
        FilesActivityPayload payload = (FilesActivityPayload) payloads.get(0);
        rootDirs = new ArrayList<>();
        N.forEach(payload.getPayload(), (stoppable, index, aFile) -> rootDirs.add(aFile));
        //ArrayAdapter<AFile> adapter = new ArrayAdapter<AFile>(this, android.R.layout.simple_list_item_1, rootDirs);
        FileAdapter adapter = new FileAdapter(this, list);
        adapter.setDirectories(payload.getPayload());
        adapter.setOnClicked(clickedDir -> {
            System.out.println("DirectoryChooserDialog.init");
            clickedDir.listDirectories();
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        runOnUiThread(adapter::notifyDataSetChanged);
    }


    private static List<String> filesToStrings(AFile[] files, NWrap.BWrap isRoot) {
        List<String> strings = new ArrayList<>();
        if (!isRoot.v())
            strings.add("..");
        N.forEach(files, (stoppable, index, aFile) -> strings.add(aFile.getName()));
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
//        List<String> storages = ExtStorageManager.getExtStorageManager().getExtSdcards();
//        AFile[] roots = N.arr.fromCollection(storages, N.converter(AFile.class, element -> AFile.instance(new File(element))));


        Intent intent = new Intent(activity, DirectoryChooserDialog.class);
        FilesActivityPayload payload = new FilesActivityPayload(AndroidDriveStrings.DIR_CHOOSER_KEY, rootDirectories);
        activity.launchActivityForResult(intent, (resultCode, result) -> {
            System.out.println("DirectoryChooserDialog.showDialog");
        }, payload);


//        List<String> strings = filesToStrings(rootDirectories, isRoot);
//        AFile[] files = rootDirectories;
//        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, strings);

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
