package de.mel.auth.data;

/**
 * Created by xor on 10/24/16.
 */
public class MelRegisterConfirm extends ServicePayload {
    private Boolean confirmed;

    public boolean isConfirmed() {
        return confirmed;
    }

    public MelRegisterConfirm setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
        return this;
    }

}
