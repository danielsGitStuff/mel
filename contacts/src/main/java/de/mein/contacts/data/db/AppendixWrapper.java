package de.mein.contacts.data.db;

/**
 * Created by xor on 10/17/17.
 */

public abstract class AppendixWrapper {
    protected ContactAppendix appendix;

    public AppendixWrapper(){
    }

    public AppendixWrapper(ContactAppendix appendix){
        this.appendix = appendix;
    }

    public AppendixWrapper setAppendix(ContactAppendix appendix) {
        this.appendix = appendix;
        return this;
    }

    public abstract String getMimeType();
}
