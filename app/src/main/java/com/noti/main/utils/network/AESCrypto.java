package com.noti.main.utils.network;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESCrypto {
    public static String encrypt(String plain, String TOKEN_KEY) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TOKEN_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] ivAndCipherText = getCombinedArray(iv, cipherText);
        return Base64.encodeToString(ivAndCipherText, Base64.NO_WRAP);
    }

    public static String decrypt(String encoded, String TOKEN_KEY) throws GeneralSecurityException {
        byte[] ivAndCipherText = Base64.decode(encoded, Base64.NO_WRAP);
        byte[] iv = Arrays.copyOfRange(ivAndCipherText, 0, 16);
        byte[] cipherText = Arrays.copyOfRange(ivAndCipherText, 16, ivAndCipherText.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(TOKEN_KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private static byte[] getCombinedArray(byte[] one, byte[] two) {
        byte[] combined = new byte[one.length + two.length];
        for (int i = 0; i < combined.length; ++i) {
            combined[i] = i < one.length ? one[i] : two[i - one.length];
        }
        return combined;
    }

    public static String parseAESToken(String string) {
        if (string.length() == 32) return string;
        string += "D~L*e/`/Q*a&h~e0jy$zU!sg?}X`CU*I";
        return string.substring(0, 32);
    }
}