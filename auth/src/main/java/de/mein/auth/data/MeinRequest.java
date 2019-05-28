package de.mein.auth.data;

import de.mein.Lok;
import de.mein.auth.MeinStrings;
import de.mein.auth.data.db.Certificate;
import de.mein.auth.socket.MeinSocket;
import de.mein.core.serialize.JsonIgnore;
import de.mein.core.serialize.SerializableEntity;
import org.jdeferred.impl.DeferredObject;

import java.security.SecureRandom;

/**
 * Created by xor on 4/28/16.
 */
public class MeinRequest extends MeinMessage {
    private Long requestId;
    private Long answerId;
    @JsonIgnore
    private IRequestHandler requestHandler;
    @JsonIgnore
    private DeferredObject<SerializableEntity, ResponseException, Void> answerDeferred;
    private byte[] secret;
    private String decryptedSecret;
    private String userUuid;
    private Boolean authenticated;
    private Certificate certificate;
    /**
     * see @{@link MeinSocket}
     */
    private String mode;

    public MeinRequest() {
        this.answerDeferred = new DeferredObject<>();
        this.requestId = new SecureRandom().nextLong();
    }

    public MeinRequest(String serviceUuid, String intent) {
        this();
        this.serviceUuid = serviceUuid;
        this.intent = intent;
    }

    public Long getRequestId() {
        return requestId;
    }

    public MeinRequest setRequestId(Long requestId) {
        this.requestId = requestId;
        return this;
    }


    public MeinResponse reponse() {
        return new MeinResponse().setResponseId(this.requestId);
    }

    public MeinRequest setRequestHandler(IRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        return this;
    }

    public MeinRequest queue() {
        requestHandler.queueForResponse(this);
        return this;
    }

    public MeinRequest setPayLoad(ServicePayload payLoad) {
        return (MeinRequest) super.setPayLoad(payLoad);
    }

    public Long getAnswerId() {
        return answerId;
    }

    public MeinRequest setAnswerId(Long answerId) {
        this.answerId = answerId;
        return this;
    }

    public DeferredObject<SerializableEntity, ResponseException, Void> getAnswerDeferred() {
        return answerDeferred;
    }

    public byte[] getSecret() {
        return secret;
    }

    public MeinRequest setSecret(byte[] secret) {
        this.secret = secret;
        return this;
    }

    public String getDecryptedSecret() {
        return decryptedSecret;
    }

    public MeinRequest setDecryptedSecret(String decryptedSecret) {
        this.decryptedSecret = decryptedSecret;
        return this;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public MeinRequest setUserUuid(String userUuid) {
        this.userUuid = userUuid;
        return this;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }

    public MeinRequest setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public MeinRequest setCertificate(Certificate certificate) {
        this.certificate = certificate;
        return this;
    }

    public MeinRequest request() {
        return new MeinRequest().setAnswerId(requestId);
    }

    @Override
    public MeinMessage setState(String state) {
        return super.setState(state);
    }

    public MeinResponse respondError(Exception e) {
        MeinResponse response = reponse().setState(MeinStrings.msg.STATE_ERR);
        if (e == null)
            return response;
        if (e instanceof ResponseException)
            response.setException((ResponseException) e);
        else {
            response.setException(new ResponseException(e));
        }
        return response;
    }

    public void setMode(String mode) {
        Lok.debug("");
        this.mode = mode;
    }
}
