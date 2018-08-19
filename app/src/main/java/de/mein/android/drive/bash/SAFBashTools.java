package de.mein.android.drive.bash;

import android.content.Context;

import java.io.IOException;
import java.util.Iterator;

import de.mein.auth.file.AFile;
import de.mein.drive.bash.BashToolsJava;

class SAFBashTools extends BashToolsJava {
    private final Context context;

    public SAFBashTools(Context context) {
        this.context = context;
    }

    @Override
    public Iterator<String> find(AFile directory, AFile pruneDir) throws IOException {
        return super.find(directory, pruneDir);
    }
}
