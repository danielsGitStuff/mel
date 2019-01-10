package de.mein.android.file;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.File;
import java.io.IOException;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import de.mein.android.MeinActivity;
import de.mein.android.Tools;

public class SAFAccessor {

    public static String getExternalSDPath() {
        return Tools.getSharedPreferences().getString(EXT_SD_CARD_PATH, null);
    }

    public static class SAFException extends Exception {
    }

    public static final String EXT_SD_CARD_URI = "extsdcarduri";
    public static final String EXT_SD_CARD_PATH = "extPath";
    public static final String MIME_GENERIC = "application/octet-stream";

    /**
     * finds the path to the external sd card and stores it.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static void setupExternalPath() {
        File externalRoot = null;
        File[] candidates = Tools.getApplicationContext().getExternalFilesDirs("external");
        File notThis = Tools.getApplicationContext().getExternalFilesDir("external");
        for (File candidate : candidates) {
            if (candidate != null && !candidate.equals(notThis)) {
                int cut = candidate.getAbsolutePath().lastIndexOf("/Android/data");
                if (cut > 0) {
                    String path = candidate.getAbsolutePath().substring(0, cut); // let the last slash there
                    externalRoot = new File(path);
                }
            }
        }
        if (externalRoot != null) {
            Tools.getSharedPreferences().edit()
                    .putString(EXT_SD_CARD_PATH, externalRoot.getAbsolutePath() + File.separator)
                    .commit();
        }
    }

    /**
     * @return
     * @throws SAFException when there is no URI to the external storage. you should setup it before. go and askForExternalRootDirectory() before.
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static DocumentFile getExternalRootDocFile() throws SAFException {
        String treeUriString = Tools.getSharedPreferences().getString(SAFAccessor.EXT_SD_CARD_URI, null);
        if (treeUriString != null) {
            Uri treeUri = Uri.parse(treeUriString);
            DocumentFile rootFile = DocumentFile.fromTreeUri(Tools.getApplicationContext(), treeUri);
            if (rootFile != null)
                return rootFile;
        }
        throw new SAFException();
    }

    /**
     * Tests wether or not we can write to the external storage. We get the root directory of it and attempt to write a dummy file there.
     * If that works the dummy file is deleted.
     *
     * @return
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    public static boolean canWriteExternal() {
        try {
            String rootPath = Tools.getSharedPreferences().getString(EXT_SD_CARD_PATH, null);
            if (rootPath != null) {
                File dummyFile;
                Integer count = 0;
                do {
                    String dummyPath = rootPath + (count++).toString() + ".txt";
                    dummyFile = new File(dummyPath);
                } while (dummyFile.exists());
                DocumentFile rootDirDoc = getExternalRootDocFile();
                DocumentFile dummyDoc = rootDirDoc.createFile("text/plain", dummyFile.getName());
                boolean fileExistsToo = dummyFile.exists();
                if (dummyDoc != null && dummyDoc.exists() && dummyDoc.canWrite() && fileExistsToo) {
                    dummyDoc.delete();
                    return true;
                } else {
                    dummyDoc.delete();
                }
            }
            return false;
        } catch (SAFException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasExternalSdCard(){
        String rootPath = Tools.getSharedPreferences().getString(EXT_SD_CARD_PATH, null);
        return rootPath != null;
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static Promise<Void, Exception, Void> askForExternalRootDirectory(MeinActivity activity) {
        DeferredObject<Void, Exception, Void> deferred = new DeferredObject<>();
        Intent docIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        docIntent.addCategory(Intent.CATEGORY_DEFAULT);


        activity.launchActivityForResult(Intent.createChooser(docIntent, "choose directory"), (resultCode, result) -> {
            if (resultCode == AppCompatActivity.RESULT_OK) {
                Uri rootTreeUri = result.getData();
                final int takeFlags = result.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.getContentResolver().takePersistableUriPermission(rootTreeUri, takeFlags);
                Tools.getSharedPreferences().edit()
                        .putString(SAFAccessor.EXT_SD_CARD_URI, rootTreeUri.toString())
                        .commit();
            }
            if (SAFAccessor.canWriteExternal()) {
                deferred.resolve(null);
                return;
            }
            SAFAccessor.askForExternalRootDirectory(activity);

        });
        return deferred;
    }
    public static void askForSuperUser() throws IOException {
        Process p = Runtime.getRuntime().exec("su");
    }
}
