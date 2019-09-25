package de.mel.auth.data;

import de.mel.Lok;

/**
 * Created by xor on 12/15/16.
 */
public class IsolationDetails extends ServicePayload {
    private String targetService;
    private String sourceService;
    private String processClass;
    private String isolationUuid;

    public String getSourceService() {
        return sourceService;
    }

    public String getTargetService() {
        return targetService;
    }

    public IsolationDetails setSourceService(String sourceService) {
        this.sourceService = sourceService;
        return this;
    }

    public IsolationDetails setProcessClass(String processClass) {
        this.processClass = processClass;
        return this;
    }

    public IsolationDetails setIsolationUuid(String isolationUuid) {
        this.isolationUuid = isolationUuid;
        return this;
    }

    public String getIsolationUuid() {
        return isolationUuid;
    }

    public IsolationDetails setTargetService(String targetService) {
        this.targetService = targetService;
        return this;
    }


    public String getProcessClass() {
        return processClass;
    }
}
