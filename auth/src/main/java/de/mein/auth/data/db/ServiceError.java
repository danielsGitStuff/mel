package de.mein.auth.data.db;

import de.mein.auth.service.BootException;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;

public class ServiceError implements SerializableEntity {
    private String exceptionClass, exceptionMessage;
    private String[] stacktrace;

    public ServiceError() {

    }

    public ServiceError(BootException e) {
        if (e.getCause() != null)
            exceptionClass = e.getCause().getClass().getSimpleName();
        if (e.getMessage() != null)
            exceptionMessage = e.getMessage();
        if (e.getStackTrace() != null)
            stacktrace = N.arr.cast(e.getStackTrace(), N.converter(String.class, tr -> tr.getFileName() + "," + tr.getMethodName() + "," + tr.getLineNumber()));
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public String[] getStacktrace() {
        return stacktrace;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public void setStacktrace(String[] stacktrace) {
        this.stacktrace = stacktrace;
    }
}
