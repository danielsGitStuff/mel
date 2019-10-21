package de.mel.auth.gui;

import com.sun.javafx.application.HostServicesDelegate;
import de.mel.Lok;
import de.mel.Versioner;
import de.mel.auth.FxApp;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.F;
import de.mel.auth.tools.N;
import de.mel.update.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AboutFX extends AuthSettingsFX {

    @FXML
    private Button btnOpenBrowser;

    @FXML
    private WebView webView;
    @FXML
    private Label lblVersion, lblVariant, lblCommit;

    @FXML
    private Button btnCheckUpdate, btnUpdate;

    //    private VersionAnswer.VersionEntry versionEntry;
    private Updater updater;

    @Override
    public boolean onPrimaryClicked() {


        return false;
    }

    @Override
    public void init() {
        try {
            WebEngine webengine = webView.getEngine();
            String content = "could not read licenses files :(";
            try {
                content = F.readResourceToString("/de/mel/auth/licences.html");
            } catch (IOException e) {
                Lok.error("failed to load licences.html");
            }
            webengine.loadContent(content);
        } catch (NullPointerException e) {
            // workaround for if the web stuff of javafx might be unavailable
            Lok.error("failed to load web engine!");
            N.r(() -> {
                String content = F.readResourceToString("/de/mel/auth/licences.html");
                File target = new File(melAuthService.getWorkingDirectory(), "licences.html");
                Path path = Paths.get(target.toURI());
                Files.write(path, content.getBytes());
                btnOpenBrowser.setOnAction(event -> {
                    HostServicesDelegate hostServices = HostServicesDelegate.getInstance(FxApp.getInstance());
                    hostServices.showDocument(target.toURI().toString());
                });
            });
        }

        try {
            lblCommit.setText(Versioner.getCommit());
            lblVariant.setText(Versioner.getBuildVariant());
            lblVersion.setText(Versioner.getVersion());
        } catch (Exception e) {
            e.printStackTrace();
        }
        btnCheckUpdate.setOnMouseClicked(event -> {
            AtomicReference<VersionAnswerEntry> versionEntry = new AtomicReference<>();
            File currentJarFile = null;
            try {
                currentJarFile = new File(CurrentJar.getCurrentJarClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!currentJarFile.getAbsolutePath().endsWith(".jar")) {
                    Lok.error("Seems I am not a jar. I won't update myself then ;)");
                    FxApp.showErrorDialog(getString("about.alert.title"), getString("about.err.notAJar"));
                    return;
                }
                Lok.debug("current path is " + currentJarFile.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
            }
            File finalCurrentJarFile = currentJarFile;
            melAuthService.getUpdater().addUpdateHandler(new UpdateHandler() {


                @Override
                public void onUpdateFileReceived(Updater updater, VersionAnswerEntry versionEntry, File updateFile) {
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
                }

                @Override
                public void onUpdateAvailable(Updater updater, VersionAnswerEntry ve) {
                    Lok.debug("available");
                    N.r(() -> {
                        String currentVersion = Versioner.getVersion();
                        Lok.debug("update available from " + currentVersion + " to " + ve.getVersion() + ", hash " + ve.getHash());
                        AboutFX.this.updater = updater;
                        versionEntry.set(ve);
                        btnUpdate.setDisable(false);
                        // let the update button have its purpose
                        btnUpdate.setOnMouseClicked(event -> {
                            Lok.debug("retrieving update");
                            File targetFile = new File(melAuthService.getWorkingDirectory(), "update.jar");
                            updater.loadUpdate(versionEntry.get(), targetFile);
                        });
                    });
                }

                @Override
                public void onNoUpdateAvailable(Updater updater) {
                    Lok.debug("no new version available");
                    FxApp.showInfoDialog(getString("about.alert.title"), getString("about.alert.noNewVersion"));
                }
            });

            N.r(() -> melAuthService.getUpdater().searchUpdate());
        });

    }

    @Override
    public String getTitle() {
        return getString("about.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.hideBottomButtons();
    }
}
