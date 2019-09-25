package de.mel.auth.data;

/**
 * Created by xor on 10/24/16.
 */
public class MelRegisterConfirm extends ServicePayload {
    private Boolean confirmed;
    private String answerUuid;

    public boolean isConfirmed() {
        return confirmed;
    }

    public MelRegisterConfirm setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
        return this;
    }

    public MelRegisterConfirm setAnswerUuid(String answerUuid) {
        this.answerUuid = answerUuid;
        return this;
    }

    public String getAnswerUuid() {
        return answerUuid;
    }
}
