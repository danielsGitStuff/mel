package de.mein.auth.data;

/**
 * Created by xor on 10/24/16.
 */
public class PartnerDidNotTrustException extends Exception {
    public PartnerDidNotTrustException(){
        super("Sorry, but your partner has some trust issues :(");
    }
}
