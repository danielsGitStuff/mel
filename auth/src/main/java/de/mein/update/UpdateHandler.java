package de.mein.update;

import java.io.File;

import de.mein.auth.file.AFile;

public interface UpdateHandler {

    void onUpdateFileReceived(Updater updater, VersionAnswer.VersionEntry versionEntry, File updateFile);

    void onProgress(Updater updater, Long done, Long length);

    void onUpdateAvailable(Updater updater, VersionAnswer.VersionEntry versionEntry);
}
