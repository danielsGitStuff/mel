package de.mel.contacts.gui;

import de.mel.Lok;
import de.mel.auth.data.db.ServiceJoinServiceType;
import de.mel.auth.gui.ServiceSettingsFX;
import de.mel.auth.service.MelAuthAdminFX;
import de.mel.contacts.service.ContactsService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;


public abstract class ContactsFXEditBaseController extends ServiceSettingsFX<ContactsService> {

    @FXML
    protected Pane pane;
    @FXML
    protected Label lblRole, lblHint;

    protected ContactsService contactsService;

    @Override
    public void init() {
        Lok.debug("ContactsFXEditBaseController.init");
    }

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        lblRole.setText(this.service.getSettings().getRole());
        Lok.debug("ContactsFXEditBaseController.feed");
        lblHint.prefWidthProperty().bind(pane.widthProperty());
    }

    @Override
    public String getTitle() {
        return getString("edit.title");
    }

    @Override
    public void configureParentGui(MelAuthAdminFX melAuthAdminFX) {
        melAuthAdminFX.setPrimaryButtonText(getString("edit.apply"));
        melAuthAdminFX.setSecondaryButtonText(getString("edit.delete"));
    }
}
