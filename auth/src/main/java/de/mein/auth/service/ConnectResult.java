package de.mein.auth.service;

import de.mein.auth.socket.process.val.MeinValidationProcess;

/**
 * Created by xor on 11/23/17.
 */

public class ConnectResult {


    private MeinValidationProcess validationProcess;
    private Exception exception;

    public void setValidationProcess(MeinValidationProcess validationProcess) {
        this.validationProcess = validationProcess;
    }

    public MeinValidationProcess getValidationProcess() {
        return validationProcess;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public boolean successful() {
        return validationProcess != null && exception == null;
    }
}
