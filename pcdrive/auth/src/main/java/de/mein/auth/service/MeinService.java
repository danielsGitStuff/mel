package de.mein.auth.service;

/**
 * Created by xor on 5/2/16.
 */
public abstract class MeinService implements IMeinService{
    protected MeinAuthService meinAuthService;
    protected Integer id;
    protected String uuid;

    public MeinService(MeinAuthService meinAuthService){
        this.meinAuthService = meinAuthService;
    }

    public Integer getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"."+meinAuthService.getName();
    }


    public void setUuid(String uuid) {
        this.uuid = uuid;
    }


}
