package de.mein.contacts.gui;

import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.gui.ServiceSettingsFX;
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
        System.out.println("ContactsFXEditBaseController.init");
    }

    @Override
    public void feed(ServiceJoinServiceType serviceJoinServiceType) {
        super.feed(serviceJoinServiceType);
        lblRole.setText(this.service.getSettings().getRole());
        System.out.println("ContactsFXEditBaseController.feed");
        lblHint.prefWidthProperty().bind(pane.widthProperty());
    }

    @Override
    public String getTitle() {
        return "Contacts";
    }



}
