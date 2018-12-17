package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.F;
import de.mein.auth.tools.N;
import de.mein.update.UpdateHandler;
import de.mein.update.Updater;
import de.mein.update.VersionAnswer;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AboutFX extends AuthSettingsFX {

    @FXML
    private WebView webView;
    @FXML
    private Label lblVersion, lblVariant;

    @FXML
    private Button btnCheckUpdate, btnUpdate;

    @Override
    public void onPrimaryClicked() {


    }

    @Override
    public void init() {
        WebEngine webengine = webView.getEngine();
        String content = "could not read licenses files :(";
        try {
            content = F.readResourceToString("/de/mein/auth/licenses.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
        webengine.loadContent(content);
        try {
            Date veriosnDate = new Date(Versioner.getBuildVersion());
            lblVersion.setText(veriosnDate.toString());
            lblVariant.setText(Versioner.getBuildVariant());
        } catch (Exception e) {
            e.printStackTrace();
        }
        btnCheckUpdate.setOnMouseClicked(event -> {
            File currentJarFile = null;
            try {
                currentJarFile = new File(AboutFX.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                Lok.debug("current path is " + currentJarFile.getAbsolutePath());
                if (!currentJarFile.getAbsolutePath().endsWith(".jar")) {
                    Lok.error("Seems I am not a jar. I won't update myself then ;)");
                    return;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            File finalCurrentJarFile = currentJarFile;
            meinAuthService.getUpdater().addUpdateHandler(new UpdateHandler() {
                @Override
                public void onUpdateFileReceived(Updater updater, VersionAnswer.VersionEntry versionEntry, File updateFile) {
                    Lok.debug("received. replacing...");
                    boolean replaced = updateFile.renameTo(finalCurrentJarFile);
                    if (replaced) {
                        final String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                        final List<String> command = new ArrayList<>();
                        command.add(javaBin);
                        command.add("-jar");
                        command.add(finalCurrentJarFile.getPath());
                        final ProcessBuilder builder = new ProcessBuilder(command);
                        N.r(builder::start);
                        System.exit(0);
                    } else {
                        Lok.error("could not replace " + finalCurrentJarFile.getAbsolutePath() + " with the downloaded version.");
                    }
                }

                @Override
                public void onProgress(Updater updater, Long done, Long length) {
                    Lok.debug("update: " + done + "/" + length);
                }

                @Override
                public void onUpdateAvailable(Updater updater, VersionAnswer.VersionEntry versionEntry) {
                    Lok.debug("available");
                    N.r(() -> {
                        Long currentVersion = Versioner.getBuildVersion();
                        if (currentVersion < versionEntry.getVersion()) {
                            Lok.debug("loading update from " + currentVersion + " to " + versionEntry.getVersion() + ", hash " + versionEntry.getHash());
                            File targetFile = new File(meinAuthService.getWorkingDirectory(), "update.jar");
                            updater.loadUpdate(versionEntry, targetFile);
                        }else {
                            Lok.debug("no new version available");
                        }
                    });
                }
            });
            N.r(() -> meinAuthService.getUpdater().retrieveUpdate());
        });
    }

    @Override
    public String getTitle() {
        return "About";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.hideBottomButtons();
    }
}
