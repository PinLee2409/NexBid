package com.nexbid.auction.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.entity.Auction;

import jakarta.persistence.LockModeType;

public interface AuctionRepository
        extends JpaRepository<Auction, UUID>, JpaSpecificationExecutor<Auction> {

    /**
     * EN: Reads the lot with a database row lock (guide §21). Everyone else bidding on this lot waits at
     *     this line, so each one reads a price that already includes the bid before it.
     * VI: Đọc lô kèm khoá dòng ở database (guide §21). Những người khác cùng trả giá lô này phải đợi ngay
     *     tại dòng này, nên mỗi người đọc được mức giá đã tính cả lượt trước đó.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") UUID id);

    List<Auction> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    Optional<Auction> findByIdAndSellerId(UUID id, UUID sellerId);

    /**
     * EN: Is this product already committed to a live auction? The database enforces it too, but asking
     *     first turns a constraint violation into a readable error.
     * VI: Sản phẩm này đã cam kết cho phiên nào còn sống chưa? Database cũng chặn, nhưng hỏi trước biến
     *     một lỗi ràng buộc thành câu trả lời đọc được.
     */
    boolean existsByProductIdAndStatusIn(UUID productId, Collection<AuctionStatus> statuses);

    /**
     * EN: Oldest first — a review queue should be fair to whoever waited longest.
     * VI: Cũ nhất trước — hàng chờ duyệt nên công bằng với người đợi lâu nhất.
     */
    List<Auction> findByStatusOrderByCreatedAtAsc(AuctionStatus status);
}
