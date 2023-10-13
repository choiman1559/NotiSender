package com.noti.main.utils.network;

import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HMACCrypto {

    public synchronized static String encrypt(String plain, String hashToken, @Nullable String token) throws GeneralSecurityException {
        byte[] iv = new byte[16];
        byte[] cipherText;

        new SecureRandom().nextBytes(iv);
        if(token != null) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AESCrypto.parseAESToken(token).getBytes(), "AES"), new IvParameterSpec(iv));
            cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        } else {
            cipherText = plain.getBytes(StandardCharsets.UTF_8);
        }

        byte[] data = iv;
        final String HMacAlgorithm = "HmacSHA256";
        SecretKeySpec secretKey = new SecretKeySpec(generateToken(hashToken).getBytes(), HMacAlgorithm);
        Mac hasher = Mac.getInstance(HMacAlgorithm);

        hasher.init(secretKey);
        hasher.update(iv);
        hasher.update(cipherText);

        data = getCombinedArray(data, hasher.doFinal());
        data = getCombinedArray(data, cipherText);
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public synchronized static String decrypt(String plain, String hashToken, @Nullable String token) throws GeneralSecurityException {
        byte[] rawByteArray = Base64.decode(plain, Base64.NO_WRAP);
        byte[] iv = Arrays.copyOfRange(rawByteArray, 0, 16);
        byte[] cipherText;

        byte[] hash = Arrays.copyOfRange(rawByteArray, 16, 48);
        cipherText = Arrays.copyOfRange(rawByteArray, 48, rawByteArray.length);

        final String HMacAlgorithm = "HmacSHA256";
        SecretKeySpec secretKey = new SecretKeySpec(generateToken(hashToken).getBytes(StandardCharsets.UTF_8), HMacAlgorithm);
        Mac hasher = Mac.getInstance(HMacAlgorithm);

        hasher.init(secretKey);
        hasher.update(iv);
        hasher.update(cipherText);

        byte[] referenceHash = hasher.doFinal();
        if (!MessageDigest.isEqual(referenceHash, hash)) {
            throw new GeneralSecurityException("Could not authenticate! Please check if data is modified by unknown attacker or sent from unpaired (or maybe itself?) device");
        }

        if(token != null) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AESCrypto.parseAESToken(token).getBytes(), "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } else {
            return new String(cipherText, StandardCharsets.UTF_8);
        }
    }

    private static byte[] getCombinedArray(byte[] one, byte[] two) {
        byte[] combined = new byte[one.length + two.length];
        for (int i = 0; i < combined.length; ++i) {
            combined[i] = i < one.length ? one[i] : two[i - one.length];
        }
        return combined;
    }

    public static String generateToken(String string) {
        return (string + "S.F#5m:TC]baX08m7U/{kjtWjx}#5Wu2").substring(0, 32);
    }

    public static String generateTokenIdentifier(String string1, String string2) {
        String[] saltArray = {"md9kg?,UWeUvbZN/", "aev0.)EJ/fn#u0bn"};
        String finalString = "";

        if(string1.length() >= string2.length()) {
            finalString += (string1 + saltArray[0]).substring(0, 16);
            finalString += (string2 + saltArray[1]).substring(0, 16);
        } else {
            finalString += (string1 + saltArray[1]).substring(0, 16);
            finalString += (string2 + saltArray[0]).substring(0, 16);
        }

        return finalString.substring(0, 32);
    }
}
