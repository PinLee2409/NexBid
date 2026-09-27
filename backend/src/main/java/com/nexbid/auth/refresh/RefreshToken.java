package com.nexbid.auth.refresh;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: One refresh token, known only by its hash. Tokens of the same sign-in share a family.
 * VI: Một refresh token, chỉ biết qua hash của nó. Các token của cùng một lần đăng nhập chung một họ.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private boolean remember;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    RefreshToken(UUID userId, UUID familyId, String tokenHash, boolean remember, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.remember = remember;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public boolean isRemember() {
        return remember;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** EN: A refresh already swapped this token for another. / VI: Một lần refresh đã đổi token này lấy token khác. */
    boolean wasReplaced() {
        return replacedBy != null;
    }

    boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    void replaceWith(UUID next, Instant now) {
        this.revokedAt = now;
        this.replacedBy = next;
    }
}
