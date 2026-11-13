package com.nexbid.payment.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.payment.entity.PaymentTransaction;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    /**
     * EN: Locked, so an IPN and the buyer's return landing at the same moment settle the attempt once.
     * VI: Có khoá, để IPN và lúc người mua quay về cùng tới một lúc thì lần thử vẫn chỉ được chốt một lần.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.txnRef = :txnRef")
    Optional<PaymentTransaction> findByTxnRefForUpdate(@Param("txnRef") String txnRef);

    /** EN: The attempt that paid, if any. / VI: Lần thử đã trả tiền, nếu có. */
    Optional<PaymentTransaction> findFirstByPaymentIdAndStatusOrderByUpdatedAtDesc(UUID paymentId, String status);
}
