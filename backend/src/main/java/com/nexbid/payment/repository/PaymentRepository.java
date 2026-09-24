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

import com.nexbid.payment.entity.Payment;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * EN: Locked, so paying and expiring the same row can never both happen.
     * VI: Có khoá, để việc thanh toán và việc hết hạn trên cùng một dòng không bao giờ cùng xảy ra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    List<Payment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * EN: Open payments past their deadline, locked. A payment that succeeds while this waits drops out,
     *     because the database re-checks the status once the lock frees.
     * VI: Các khoản còn mở đã quá hạn, có khoá. Khoản nào vừa thanh toán xong trong lúc chờ sẽ bị loại, vì
     *     database kiểm lại trạng thái khi khoá được mở.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.status IN (com.nexbid.payment.PaymentStatus.PENDING, "
            + "com.nexbid.payment.PaymentStatus.FAILED) AND p.expiredAt <= :now ORDER BY p.expiredAt")
    List<Payment> findOverdue(@Param("now") Instant now, Pageable pageable);
}
