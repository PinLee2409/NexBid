package com.nexbid.auth;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import tools.jackson.databind.JsonNode;

/**
 * EN: Checks the ID token Google's sign-in button hands the browser: signed by one of Google's published keys (fetched
 *     and kept an hour), issued by Google, for this site's client id, not expired, and for a verified email. Off until
 *     NEXBID_GOOGLE_CLIENT_ID is set.
 * VI: Kiểm ID token mà nút đăng nhập Google đưa cho trình duyệt: ký bằng một trong các khoá Google công bố (tải về và giữ
 *     một giờ), do Google cấp, cho client id của site này, chưa hết hạn, và email đã xác minh. Tắt cho tới khi đặt
 *     NEXBID_GOOGLE_CLIENT_ID.
 */
@Component
class GoogleSignIn {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
    private static final Duration KEEP_KEYS = Duration.ofHours(1);

    /** EN: Who Google says signed in. / VI: Người mà Google nói đã đăng nhập. */
    record Identity(String subject, String email, String name) {
    }

    private final String clientId;
    private final RestClient http;
    private final String keysUrl;
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant keysUntil = Instant.EPOCH;

    GoogleSignIn(@Value("${nexbid.auth.google.client-id:}") String clientId,
            @Value("${nexbid.auth.google.keys-url:https://www.googleapis.com/oauth2/v3/certs}") String keysUrl) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.keysUrl = keysUrl;
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(5));
        timeouts.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(timeouts).build();
    }

    boolean enabled() {
        return !clientId.isEmpty();
    }

    String clientId() {
        return clientId;
    }

    Identity verify(String credential) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.GOOGLE_SIGN_IN_OFF, "Google sign-in is not set up");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<java.security.Key>() {
                        @Override
                        protected java.security.Key locate(ProtectedHeader header) {
                            return key(header.getKeyId());
                        }
                    })
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(credential)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw refused("Google's token could not be verified");
        }
        if (!ISSUERS.contains(claims.getIssuer())) {
            throw refused("The token was not issued by Google");
        }
        Object verified = claims.get("email_verified");
        String email = claims.get("email", String.class);
        if (email == null || !(Boolean.TRUE.equals(verified) || "true".equals(verified))) {
            throw refused("Google has not verified this email address");
        }
        String name = claims.get("name", String.class);
        return new Identity(claims.getSubject(), email.trim().toLowerCase(java.util.Locale.ROOT),
                name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim());
    }

    private PublicKey key(String keyId) {
        if (keyId == null) {
            throw refused("The token names no key");
        }
        PublicKey found = Instant.now().isBefore(keysUntil) ? keys.get(keyId) : null;
        if (found == null) {
            reload();
            found = keys.get(keyId);
        }
        if (found == null) {
            throw refused("Unknown Google key");
        }
        return found;
    }

    private synchronized void reload() {
        JsonNode body;
        try {
            body = http.get().uri(keysUrl).retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new BusinessException(ErrorCode.GOOGLE_SIGN_IN_OFF, "Google's keys could not be fetched");
        }
        Map<String, PublicKey> loaded = new ConcurrentHashMap<>();
        for (JsonNode jwk : body.path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asString())) {
                continue;
            }
            try {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asString()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asString()));
                loaded.put(jwk.path("kid").asString(),
                        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {
                // EN: A key we cannot read is skipped; the others still work. / VI: Khoá không đọc được thì bỏ qua; các khoá khác vẫn dùng được.
            }
        }
        keys = loaded;
        keysUntil = Instant.now().plus(KEEP_KEYS);
    }

    private static BusinessException refused(String why) {
        return new BusinessException(ErrorCode.GOOGLE_SIGN_IN_FAILED, why);
    }
}
