package com.nexbid.auth.refresh;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * EN: Locked, so two requests presenting the same token are served one after the other: the second sees it
     *     already replaced.
     * VI: Có khoá, để hai request gửi cùng một token được xử lý lần lượt: request thứ hai thấy token đã bị thay.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :hash")
    Optional<RefreshToken> findLockedByHash(@Param("hash") String hash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.familyId = :family AND t.revokedAt IS NULL")
    int revokeFamily(@Param("family") UUID family, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :user AND t.revokedAt IS NULL")
    int revokeAllOf(@Param("user") UUID user, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
