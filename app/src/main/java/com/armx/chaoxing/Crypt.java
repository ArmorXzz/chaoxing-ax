package com.armx.chaoxing;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 与旧版 server.py 完全同构的加解密：AES-CBC / key = iv / PKCS7 / Base64。 */
final class Crypt {

    static final byte[] KEY = "u2oh6Vu^HWe4_AES".getBytes(StandardCharsets.UTF_8);

    private Crypt() {
    }

    static String encrypt(String text) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(KEY));
            byte[] out = c.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    static String decrypt(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(KEY));
            byte[] out = c.doFinal(Base64.decode(b64, Base64.NO_WRAP));
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                int v = b & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
