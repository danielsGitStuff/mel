package de.mel.update;

import java.io.File;

import de.mel.auth.file.AFile;

public interface UpdateHandler {

    void onUpdateFileReceived(Updater updater, VersionAnswerEntry versionEntry, File updateFile);

    void onProgress(Updater updater, Long done, Long length);

    void onUpdateAvailable(Updater updater, VersionAnswerEntry versionEntry);

    void onNoUpdateAvailable(Updater updater);
}
