package de.mel.auth.gui;

import de.mel.auth.data.db.Certificate;
import de.mel.auth.data.db.CertificateAddress;
import de.mel.auth.gui.controls.CertListCell;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.auth.tools.AddressParser;
import de.mel.auth.tools.N;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by xor on 3/27/17.
 */
public class OthersSettingsFX extends AuthSettingsFX {
    @FXML
    private ListView<Certificate> listCerts;

    private Certificate selectedCert;

    @Override
    public boolean onPrimaryClicked(MelAuthAdminFX melAuthAdminFX) {
        N.r(() -> {
            if (selectedCert != null) {
                melAuthService.getCertificateManager().deleteCertificate(selectedCert);
                showCerts();
            }
        });
        return false;
    }

    @Override
    public void init() {
        listCerts.setCellFactory(param -> new CertListCell());
        listCerts.getItems().clear();
        showCerts();
        listCerts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedCert = selected;
            } else {
                selectedCert = null;
            }
        });
    }

    private void showCerts() {
        N.r(() -> {
            selectedCert = null;
            listCerts.getItems().setAll(melAuthService.getTrustedCertificates());
        });
    }

    @Override
    public String getTitle() {
        return getString("others.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("delete"));
        melAuthAdminFX.setSecondaryButtonText(getString("others.editAddresses"));
        melAuthAdminFX.showBottomButtons();
    }

    @Override
    public void onSecondaryClicked(MelAuthAdminFX melAuthAdminFX) {
        if (selectedCert == null || selectedCert.getId().v() == null)
            return;
        N.r(() -> openEditAddressesDialog(melAuthAdminFX, selectedCert));
    }

    private void openEditAddressesDialog(MelAuthAdminFX melAuthAdminFX, Certificate cert) throws Exception {
        List<CertificateAddress> existing = melAuthService.getCertificateManager().getAlternativeAddresses(cert.getId().v());
        ObservableList<CertificateAddress> items = FXCollections.observableArrayList(existing);

        Dialog<ButtonType> dialog = new Dialog<>();
        if (melAuthAdminFX.getStage() != null)
            dialog.initOwner(melAuthAdminFX.getStage());
        dialog.setTitle(getString("others.editAddresses") + " - " + cert.getName().v());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<CertificateAddress> addressList = new ListView<>(items);
        addressList.setPrefHeight(180);
        addressList.setCellFactory(lv -> new ListCell<CertificateAddress>() {
            @Override
            protected void updateItem(CertificateAddress item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : AddressParser.format(item));
            }
        });

        TextField input = new TextField();
        input.setPromptText(getString("others.addresses.prompt"));
        HBox.setHgrow(input, Priority.ALWAYS);

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c62828;");

        Button btnAdd = new Button(getString("others.addresses.add"));
        btnAdd.setOnAction(e -> {
            try {
                CertificateAddress parsed = AddressParser.parse(input.getText());
                for (CertificateAddress item : items) {
                    if (Objects.equals(item.getAddress().v(), parsed.getAddress().v())
                            && Objects.equals(item.getPort().v(), parsed.getPort().v())) {
                        errorLabel.setText(getString("others.addresses.duplicate"));
                        return;
                    }
                }
                items.add(parsed);
                input.clear();
                errorLabel.setText("");
            } catch (IllegalArgumentException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });

        Button btnUp = new Button(getString("others.addresses.up"));
        btnUp.setOnAction(e -> {
            int idx = addressList.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                CertificateAddress item = items.remove(idx);
                items.add(idx - 1, item);
                addressList.getSelectionModel().select(idx - 1);
            }
        });

        Button btnDown = new Button(getString("others.addresses.down"));
        btnDown.setOnAction(e -> {
            int idx = addressList.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < items.size() - 1) {
                CertificateAddress item = items.remove(idx);
                items.add(idx + 1, item);
                addressList.getSelectionModel().select(idx + 1);
            }
        });

        Button btnRemove = new Button(getString("others.addresses.remove"));
        btnRemove.setOnAction(e -> {
            int idx = addressList.getSelectionModel().getSelectedIndex();
            if (idx >= 0)
                items.remove(idx);
        });

        HBox addRow = new HBox(8, input, btnAdd);
        HBox actionRow = new HBox(8, btnUp, btnDown, btnRemove);
        VBox content = new VBox(8, addRow, errorLabel, addressList, actionRow);
        content.setPadding(new Insets(10));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                N.r(() -> melAuthService.getCertificateManager().replaceAlternativeAddresses(cert.getId().v(), new ArrayList<>(items)));
            }
        });
    }
}
