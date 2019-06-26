package de.mein.contacts.gui;

import de.mein.Lok;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.ServiceSettingsFX;
import de.mein.auth.service.MeinAuthAdminFX;
import de.mein.contacts.service.ContactsService;
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
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText(getString("edit.apply"));
        meinAuthAdminFX.setSecondaryButtonText(getString("edit.delete"));
    }
}
