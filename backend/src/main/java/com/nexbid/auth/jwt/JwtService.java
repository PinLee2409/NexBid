package com.nexbid.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.nexbid.user.RoleName;
import com.nexbid.user.UserCredentials;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * EN: Issues and reads access tokens. The only place that knows the signing key.
 * VI: Cấp và đọc access token. Nơi duy nhất biết khoá ký.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        // EN: HMAC-SHA needs at least 256 bits; a short secret fails here rather than silently weakening the token.
        // VI: HMAC-SHA cần tối thiểu 256 bit; secret quá ngắn sẽ lỗi ngay thay vì âm thầm làm token yếu đi.
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserCredentials user) {
        Instant now = Instant.now();

        return Jwts.builder()
                // EN: The subject is the user id, never the email — an email can change, an id cannot.
                // VI: Subject là id người dùng, không phải email — email có thể đổi, id thì không.
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("roles", user.roles().stream().map(RoleName::name).toList())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiry())))
                .signWith(key)
                .compact();
    }

    /**
     * EN: Returns the claims, or empty when the token is absent, tampered with, or expired.
     * VI: Trả về claims, hoặc rỗng khi token thiếu, bị sửa, hoặc đã hết hạn.
     */
    public java.util.Optional<AuthenticatedUser> read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            return java.util.Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    roles == null ? List.of() : roles.stream().map(RoleName::valueOf).toList()));

        } catch (JwtException | IllegalArgumentException ex) {
            // EN: Any failure means the same thing to the caller: this token is not usable.
            // VI: Mọi kiểu lỗi đều mang cùng một ý nghĩa với bên gọi: token này không dùng được.
            return java.util.Optional.empty();
        }
    }

    /** EN: Who the token says is calling. / VI: Token nói ai đang gọi. */
    public record AuthenticatedUser(UUID id, String email, List<RoleName> roles) {
    }
}
