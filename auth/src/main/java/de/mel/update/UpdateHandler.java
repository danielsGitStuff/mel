package de.mel.update;

import java.io.File;

import de.mel.auth.file.AFile;

public interface UpdateHandler {

    void onUpdateFileReceived(Updater updater, VersionAnswer.VersionEntry versionEntry, File updateFile);

    void onProgress(Updater updater, Long done, Long length);

    void onUpdateAvailable(Updater updater, VersionAnswer.VersionEntry versionEntry);

    void onNoUpdateAvailable(Updater updater);
}
