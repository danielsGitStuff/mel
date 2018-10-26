package de.mein.auth.gui;

import de.mein.Lok;
import de.mein.Versioner;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.auth.tools.N;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

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
        N.r(() -> {
            WebEngine webengine = webView.getEngine();
            URL url = AboutFX.class.getClassLoader().getResource("licenses.html");
            byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
            webengine.loadContent(new String(bytes));
//            com.sun.javafx.webkit.Accessor.getPageFor(webengine).setBackgroundColor(0);
        });
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
