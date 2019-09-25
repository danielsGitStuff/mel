package de.mel.auth.tools;

import de.mel.auth.data.db.Certificate;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;

/**
 * Created by xor on 4/18/16.
 */
public class Cryptor {

    public static byte[] encrypt(Certificate certificate, final String original) throws CertificateException, NoSuchPaddingException, NoSuchAlgorithmException, IOException, BadPaddingException, IllegalBlockSizeException, InvalidKeyException, ClassNotFoundException, NoSuchProviderException {
            return Cryptor.encrypt(certificate.getPublicKey(),original);
    }

    public static byte[] encrypt(PublicKey publicKey, final String original) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException, IllegalBlockSizeException, BadPaddingException, ClassNotFoundException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] bytes = cipher.doFinal(original.getBytes());
        return bytes;
    }

    public static String decrypt(PrivateKey privateKey, final byte[] encrypted) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException, IllegalBlockSizeException, BadPaddingException, ClassNotFoundException {
        Cipher decCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        decCipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] dec = decCipher.doFinal(encrypted);
        return new String(dec);
    }
}
