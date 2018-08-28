package de.mein.auth.data;

/**
 * Created by xor on 10/24/16.
 */
public class MeinRegisterConfirm implements IPayload {
    private Boolean confirmed;
    private String answerUuid;

    public boolean isConfirmed() {
        return confirmed;
    }

    public MeinRegisterConfirm setConfirmed(Boolean confirmed) {
        this.confirmed = confirmed;
        return this;
    }

    public MeinRegisterConfirm setAnswerUuid(String answerUuid) {
        this.answerUuid = answerUuid;
        return this;
    }

    public String getAnswerUuid() {
        return answerUuid;
    }
}
