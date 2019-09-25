package de.mel.auth.data;

import de.mel.Lok;
import de.mel.auth.MelStrings;
import de.mel.auth.data.db.Certificate;
import de.mel.auth.socket.MelSocket;
import de.mel.auth.tools.Eva;
import de.mel.core.serialize.JsonIgnore;
import de.mel.core.serialize.SerializableEntity;
import org.jdeferred.impl.DeferredObject;

import java.security.SecureRandom;

/**
 * Created by xor on 4/28/16.
 */
public class MelRequest extends MelMessage {
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
     * see @{@link MelSocket}
     */
    private String mode;

    public MelRequest() {
        this.answerDeferred = new DeferredObject<>();
        this.requestId = new SecureRandom().nextLong();
    }

    public MelRequest(String serviceUuid, String intent) {
        this();
        this.serviceUuid = serviceUuid;
        this.intent = intent;
    }

    public Long getRequestId() {
        return requestId;
    }

    public MelRequest setRequestId(Long requestId) {
        this.requestId = requestId;
        return this;
    }


    public MelResponse reponse() {
        return new MelResponse().setResponseId(this.requestId);
    }

    public MelRequest setRequestHandler(IRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        return this;
    }

    public MelRequest queue() {
        requestHandler.queueForResponse(this);
        return this;
    }

    public MelRequest setPayLoad(ServicePayload payLoad) {
        return (MelRequest) super.setPayLoad(payLoad);
    }

    public Long getAnswerId() {
        return answerId;
    }

    public MelRequest setAnswerId(Long answerId) {
        this.answerId = answerId;
        return this;
    }

    public DeferredObject<SerializableEntity, ResponseException, Void> getAnswerDeferred() {
        return answerDeferred;
    }

    public byte[] getSecret() {
        return secret;
    }

    public MelRequest setSecret(byte[] secret) {
        this.secret = secret;
        return this;
    }

    public String getDecryptedSecret() {
        return decryptedSecret;
    }

    public MelRequest setDecryptedSecret(String decryptedSecret) {
        this.decryptedSecret = decryptedSecret;
        return this;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public MelRequest setUserUuid(String userUuid) {
        this.userUuid = userUuid;
        return this;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }

    public MelRequest setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
    }

    public Certificate getCertificate() {
        return certificate;
    }

    public MelRequest setCertificate(Certificate certificate) {
        this.certificate = certificate;
        return this;
    }

    public MelRequest request() {
        return new MelRequest().setAnswerId(requestId);
    }

    @Override
    public MelMessage setState(String state) {
        return super.setState(state);
    }

    public MelResponse respondError(Exception e) {
        MelResponse response = reponse().setState(MelStrings.msg.STATE_ERR);
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
