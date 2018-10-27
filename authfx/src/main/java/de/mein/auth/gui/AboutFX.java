package de.mein.auth.gui;

import de.mein.Versioner;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.F;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AboutFX extends AuthSettingsFX {

    @FXML
    private WebView webView;
    @FXML
    private Label lblVersion;

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
        lblVersion.setText(Versioner.getBuildVersion());
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
