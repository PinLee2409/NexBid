package com.nexbid.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.user.SellerApplicationStatus;
import com.nexbid.user.entity.SellerApplication;

import jakarta.persistence.LockModeType;

/** EN: Requests to become a seller. / VI: Các yêu cầu trở thành người bán. */
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    Optional<SellerApplication> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, SellerApplicationStatus status);

    Page<SellerApplication> findByStatus(SellerApplicationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM SellerApplication a WHERE a.id = :id")
    Optional<SellerApplication> findByIdForUpdate(@Param("id") UUID id);
}
