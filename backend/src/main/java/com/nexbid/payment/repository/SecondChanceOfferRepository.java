package com.nexbid.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.payment.entity.SecondChanceOffer;

import jakarta.persistence.LockModeType;

public interface SecondChanceOfferRepository extends JpaRepository<SecondChanceOffer, UUID> {

    /** EN: Locked, so accepting and expiring the same offer can never both happen. / VI: Có khoá, để nhận và hết hạn cùng một đề nghị không bao giờ cùng xảy ra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SecondChanceOffer o WHERE o.id = :id")
    Optional<SecondChanceOffer> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByAuctionId(UUID auctionId);

    List<SecondChanceOffer> findBySellerId(UUID sellerId);

    List<SecondChanceOffer> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    /** EN: Open offers past their deadline, locked. / VI: Các đề nghị còn mở đã quá hạn, có khoá. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SecondChanceOffer o WHERE o.status = com.nexbid.payment.OfferStatus.PENDING "
            + "AND o.expiresAt <= :now ORDER BY o.expiresAt")
    List<SecondChanceOffer> findOverdue(@Param("now") Instant now, Pageable pageable);
}
