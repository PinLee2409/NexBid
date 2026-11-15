package com.nexbid.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * EN: Time-based one-time codes (RFC 6238) as authenticator apps make them: HMAC-SHA1, 30-second steps, 6 digits, the
 *     secret written in base32. A code from the step before or after is accepted, for clocks a little apart.
 * VI: Mã dùng một lần theo thời gian (RFC 6238) như các ứng dụng xác thực tạo: HMAC-SHA1, mỗi bước 30 giây, 6 chữ số,
 *     secret viết bằng base32. Mã của bước trước hoặc sau cũng được nhận, cho đồng hồ lệch nhau một chút.
 */
final class Totp {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    static final long STEP_SECONDS = 30;

    private Totp() {
    }

    /** EN: 160 random bits, base32. / VI: 160 bit ngẫu nhiên, dạng base32. */
    static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    static long stepAt(Instant at) {
        return at.getEpochSecond() / STEP_SECONDS;
    }

    static String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * EN: The step a code belongs to, if it is one of the three around now and later than {@code usedUpTo}; -1 otherwise.
     * VI: Bước mà mã thuộc về, nếu là một trong ba bước quanh hiện tại và sau {@code usedUpTo}; ngược lại là -1.
     */
    static long match(String secret, String code, Instant now, long usedUpTo) {
        if (code == null || !code.matches("\\d{6}")) {
            return -1;
        }
        long current = stepAt(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (step > usedUpTo && java.security.MessageDigest.isEqual(
                    code(secret, step).getBytes(), code.getBytes())) {
                return step;
            }
        }
        return -1;
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] unbase32(String text) {
        String clean = text.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not base32: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
