package com.nexbid.notification;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EN: The cryptography of Web Push, with the JDK alone: P-256 keys written as browsers write them, the payload
 *     encryption of RFC 8291 (aes128gcm, one record), and helpers for the VAPID key pair of RFC 8292.
 * VI: Phần mật mã của Web Push, chỉ dùng JDK: khoá P-256 viết theo cách trình duyệt viết, mã hoá nội dung theo RFC 8291
 *     (aes128gcm, một bản ghi), và các hàm phụ cho cặp khoá VAPID của RFC 8292.
 */
final class WebPush {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    private static final ECParameterSpec P256 = p256();

    private WebPush() {
    }

    static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** EN: 0x04 ‖ X ‖ Y, 65 bytes. / VI: 0x04 ‖ X ‖ Y, 65 byte. */
    static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        copy32(key.getW().getAffineX(), out, 1);
        copy32(key.getW().getAffineY(), out, 33);
        return out;
    }

    static ECPublicKey decodePublic(byte[] raw) {
        if (raw.length != 65 || raw[0] != 4) {
            throw new IllegalArgumentException("Not an uncompressed P-256 point");
        }
        try {
            ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
                    new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Not a P-256 public key", ex);
        }
    }

    static ECPrivateKey decodePrivate(byte[] pkcs8) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("The stored push key cannot be read", ex);
        }
    }

    static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] unbase64Url(String text) {
        return Base64.getUrlDecoder().decode(text.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    /**
     * EN: RFC 8291: encrypts {@code payload} for the browser holding {@code receiverKey} and {@code authSecret}. The body
     *     is salt ‖ record size ‖ key length ‖ our one-time public key ‖ the sealed record.
     * VI: RFC 8291: mã hoá {@code payload} cho trình duyệt giữ {@code receiverKey} và {@code authSecret}. Body là salt ‖
     *     kích thước bản ghi ‖ độ dài khoá ‖ khoá công khai dùng một lần của ta ‖ bản ghi đã niêm phong.
     */
    static byte[] encrypt(byte[] payload, byte[] receiverKey, byte[] authSecret) {
        try {
            ECPublicKey receiver = decodePublic(receiverKey);
            KeyPair ours = newKeyPair();
            byte[] ourKey = encode((ECPublicKey) ours.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ours.getPrivate());
            agreement.doPhase(receiver, true);
            byte[] shared = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, receiverKey, ourKey);
            byte[] ikm = expand(hmac(authSecret, shared), keyInfo, 32);

            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] prk = hmac(salt, ikm);
            byte[] contentKey = expand(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {0}), 16);
            byte[] nonce = expand(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {0}), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"), new GCMParameterSpec(128, nonce));
            // EN: 0x02 marks the last (and only) record. / VI: 0x02 đánh dấu bản ghi cuối (và duy nhất).
            byte[] sealed = cipher.doFinal(concat(payload, new byte[] {2}));

            return ByteBuffer.allocate(16 + 4 + 1 + ourKey.length + sealed.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) ourKey.length).put(ourKey).put(sealed).array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** EN: HKDF-Expand for one block, enough for every length here. / VI: HKDF-Expand một khối, đủ cho mọi độ dài ở đây. */
    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }

    private static void copy32(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - 32);
        int length = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + 32 - length, length);
    }

    private static ECParameterSpec p256() {
        return ((ECPublicKey) newKeyPairUnchecked().getPublic()).getParams();
    }

    private static KeyPair newKeyPairUnchecked() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
