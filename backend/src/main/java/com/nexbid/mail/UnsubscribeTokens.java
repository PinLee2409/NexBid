package com.nexbid.mail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * EN: The token in an email's unsubscribe link: the account id and an HMAC of it, so the link works without signing
 *     in but nobody can turn off someone else's emails by guessing an id. It never expires — an old email's link
 *     must keep working.
 * VI: Token trong link huỷ đăng ký của email: id tài khoản kèm HMAC của nó, nên link dùng được mà không cần đăng nhập,
 *     nhưng không ai tắt được email của người khác bằng cách đoán id. Không bao giờ hết hạn — link trong email cũ vẫn
 *     phải dùng được.
 */
@Component
class UnsubscribeTokens {

    private final byte[] key;

    UnsubscribeTokens(@Value("${nexbid.jwt.secret}") String secret) {
        // EN: Its own purpose label, so this HMAC can never pass for anything else signed with the secret.
        // VI: Có nhãn mục đích riêng, để HMAC này không bao giờ bị nhầm với thứ khác ký bằng cùng secret.
        this.key = ("unsubscribe:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    String tokenFor(UUID userId) {
        return userId + "." + sign(userId.toString());
    }

    Optional<UUID> userOf(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        try {
            UUID userId = UUID.fromString(token.substring(0, dot));
            byte[] expected = sign(userId.toString()).getBytes(StandardCharsets.US_ASCII);
            byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, given) ? Optional.of(userId) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available", ex);
        }
    }
}
