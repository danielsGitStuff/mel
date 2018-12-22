package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.MeinNotification;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.F;
import de.mein.auth.tools.N;
import de.mein.update.CurrentJar;
import de.mein.update.UpdateHandler;
import de.mein.update.Updater;
import de.mein.update.VersionAnswer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class AboutFX extends AuthSettingsFX {

    @FXML
    private WebView webView;
    @FXML
    private Label lblVersion, lblVariant;

    @FXML
    private Button btnCheckUpdate, btnUpdate;

    //    private VersionAnswer.VersionEntry versionEntry;
    private Updater updater;

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
            AtomicReference<VersionAnswer.VersionEntry> versionEntry = new AtomicReference<>();
            File currentJarFile = null;
            try {
                currentJarFile = new File(CurrentJar.getCurrentJarClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                if (!currentJarFile.getAbsolutePath().endsWith(".jar")) {
                    Lok.error("Seems I am not a jar. I won't update myself then ;)");
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Update Info");
                        alert.setHeaderText(null);
                        alert.setContentText("Seems this is not started from a single jar-file. I don't know where to put the update then. So I won't update.");
                        alert.showAndWait();
                    });
                    return;
                }
                Lok.debug("current path is " + currentJarFile.getAbsolutePath());
            } catch (Exception e) {
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
//                    Lok.debug("update: " + done + "/" + length);
                }

                @Override
                public void onUpdateAvailable(Updater updater, VersionAnswer.VersionEntry ve) {
                    Lok.debug("available");
                    N.r(() -> {
                        Long currentVersion = Versioner.getBuildVersion();
                        if (currentVersion < ve.getVersion()) {
                            Lok.debug("update available from " + currentVersion + " to " + ve.getVersion() + ", hash " + ve.getHash());
                            AboutFX.this.updater = updater;
                            versionEntry.set(ve);
                            btnUpdate.setDisable(false);
                            // let the update button have its purpose
                            btnUpdate.setOnMouseClicked(event -> {
                                Lok.debug("retrieving update");
                                File targetFile = new File(meinAuthService.getWorkingDirectory(), "update.jar");
                                updater.loadUpdate(versionEntry.get(), targetFile);
                            });
                        } else {
                            Lok.debug("no new version available");
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                alert.setTitle("Update Info");
                                alert.setHeaderText(null);
                                alert.setContentText("There is no new version available.");
                                alert.showAndWait();
                            });
                        }
                    });
                }
            });

            N.r(() -> meinAuthService.getUpdater().retrieveUpdate());
        });

    }

//    private File getCurrentJarFile() {
//        String prop = System.getProperty("java.class.path");
//        if (prop == null) {
//            Lok.debug("java.class.path was null");
//            return null;
//        }
//        File base = new File("f").getAbsoluteFile().getParentFile();
//        Lok.debug("comparing with base: " + base.getAbsolutePath());
//        String separator = System.getProperty("path.separator");
//        Arrays.stream(prop.split(separator)).forEach(s -> Lok.debug("path1: " + s));
//        // we assume that the applications runs from one jar.
////        File jarFile =  new File(CurrentJar.getCurrentJarClass().getProtectionDomain().getCodeSource().getLocation().toURI());
////        Arrays.stream(prop.split(separator)).forEach(s -> {
////            boolean match = s.startsWith(base.getAbsolutePath());
////            Lok.debug("part: "+s+" -> "+match);
////            N.r( () -> {
////              File f =  new File(AboutFX.class.getProtectionDomain().getCodeSource().getLocation().toURI());
////              Lok.debug(">>> "+f.getAbsolutePath());
////            });
////        });
//        Optional<String> path = Arrays.stream(prop.split(separator)).filter(s -> s.startsWith(base.getAbsolutePath())).findFirst();
//        Lok.debug("found matching path? " + path.isPresent());
//        return path.map(s -> new File(s).getAbsoluteFile()).orElse(null);
//    }

    @Override
    public String getTitle() {
        return "About";
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.hideBottomButtons();
    }
}
