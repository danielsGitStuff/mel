package de.mein.auth.gui;

import de.mein.auth.data.NetworkEnvironment;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.data.db.ServiceJoinServiceType;
import de.mein.auth.service.MeinAuthAdminFX;

public abstract class EmbeddedServiceSettingsFX extends AuthSettingsFX {

    private RemoteServiceChooserFX remoteServiceChooserFX;

    public boolean isServerSelected() {
        return remoteServiceChooserFX.isServerSelected();
    }

    public ServiceJoinServiceType getSelectedService() {
        return remoteServiceChooserFX.getSelectedService();
    }

    public Certificate getSelectedCertificate() {
        return remoteServiceChooserFX.getSelectedCertificate();
    }

    public abstract void onServiceSpotted(NetworkEnvironment.FoundServices foundServices, Long certId, ServiceJoinServiceType service);

    public void setRemoteServiceChooserFX(RemoteServiceChooserFX remoteServiceChooserFX) {
        this.remoteServiceChooserFX = remoteServiceChooserFX;
    }

    @Override
    public void configureParentGui(MeinAuthAdminFX meinAuthAdminFX) {
        meinAuthAdminFX.setPrimaryButtonText("Apply");
        meinAuthAdminFX.showPrimaryButtonOnly();
    }

    /**
     * server radio button selected
     */
    public void onRbServerSelected() {

    }

    /**
     * client radio button selected
     */
    public void onRbClientSelected() {

    }

    /**
     * user wants to create a client and selected a service
     * @param selectedCertificate
     * @param selectedService
     */
    public void onServiceSelected(Certificate selectedCertificate, ServiceJoinServiceType selectedService) {

    }
}
