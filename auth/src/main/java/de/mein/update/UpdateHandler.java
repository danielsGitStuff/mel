package de.mein.update;

import java.io.File;

public interface UpdateHandler {

    void onUpdateFileReceived(VersionAnswer.VersionEntry versionEntry, File updateFile);

    void onProgress(Long done, Long length);

    void onUpdateAvailable(VersionAnswer.VersionEntry versionEntry);
}
