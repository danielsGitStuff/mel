package de.mein.auth.data;

/**
 * Created by xor on 6/23/16.
 */
public class CertificateRejectedException extends Exception implements IPayload  {

    public CertificateRejectedException(){
        super();
        setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public String getMessage() {
        return "your certificate was rejected :'(";
    }
}
