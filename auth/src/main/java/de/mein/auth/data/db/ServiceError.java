package de.mein.auth.data.db;

import de.mein.Versioner;
import de.mein.auth.service.BootException;
import de.mein.auth.service.MeinAuthService;
import de.mein.auth.tools.N;
import de.mein.core.serialize.SerializableEntity;

import java.util.ArrayList;
import java.util.List;

public class ServiceError implements SerializableEntity {
    private String exceptionClass, exceptionMessage, variant;
    private List<String> stacktrace;
    private Long version;

    public ServiceError() {

    }


    public Long getVersion() {
        return version;
    }

    public String getVariant() {
        return variant;
    }

    public ServiceError(BootException e) {
        N.oneLine(() -> version = Versioner.getBuildVersion());
        N.oneLine(() -> variant = Versioner.getBuildVariant());
        if (e.getCause() != null)
            exceptionClass = e.getCause().getClass().getSimpleName();
        if (e.getMessage() != null)
            exceptionMessage = e.getMessage();
        if (e.getCause() != null && e.getCause().getStackTrace() != null) {
            stacktrace = new ArrayList<>();
            N.forEach(e.getCause().getStackTrace(), tr -> stacktrace.add(tr.getFileName() + "," + tr.getMethodName() + "," + tr.getLineNumber()));
        }
    }

    public String getExceptionClass() {
        return exceptionClass;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public List<String> getStacktrace() {
        return stacktrace;
    }

    public void setExceptionClass(String exceptionClass) {
        this.exceptionClass = exceptionClass;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

}
