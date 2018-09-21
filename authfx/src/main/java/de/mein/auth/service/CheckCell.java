package de.mein.auth.service;

import de.mein.Lok;
import de.mein.auth.data.ApprovalMatrix;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;

/**
 * Created by xor on 6/30/16.
 */
public class CheckCell extends TableCell<ServiceJoinServiceType, ServiceJoinServiceType> {
    //private final ServiceJoinServiceType service;
    private final Certificate certificate;
    private ApprovalHandler approvalHandler;
    private ApprovalMatrix approvalMatrix;

    public interface ApprovalHandler {
        void approved(Certificate certificate, ServiceJoinServiceType serviceType, boolean approved);
    }

    public CheckCell setApprovalHandler(ApprovalHandler approvalHandler) {
        this.approvalHandler = approvalHandler;
        return this;
    }

    public CheckCell(Certificate certificate, ApprovalMatrix approvalMatrix) {
        //this.service = service;
        this.certificate = certificate;
        this.approvalMatrix = approvalMatrix;
    }


    @Override
    protected void updateItem(ServiceJoinServiceType service, boolean empty) {
        super.updateItem(service, empty);
        if (service != null) {
            CheckBox checkBox = new CheckBox();
            checkBox.setSelected(approvalMatrix.isApproved(certificate.getId().v(), service.getServiceId().v()));
            setGraphic(checkBox);
            checkBox.onActionProperty().setValue(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    Lok.debug("CheckCell.handle");
                    if (approvalHandler != null) {
                        approvalHandler.approved(certificate, service, checkBox.isSelected() );
                    }
                }
            });
        }
    }
}

