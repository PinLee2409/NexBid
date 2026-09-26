package com.nexbid.auction.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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

    /**
     * EN: Lots whose start time has arrived (guide §25), oldest first and capped by the caller. Locked,
     *     because the scheduler is about to move each one and a bid must not read a half-changed lot.
     *     A lot whose whole window has already passed is not opened — it could never take a bid.
     * VI: Các lô đã tới giờ mở (guide §25), cũ nhất trước và bị giới hạn số lượng bởi bên gọi. Có khoá, vì
     *     scheduler sắp chuyển trạng thái từng lô và một lượt trả giá không được đọc lô đang đổi dở.
     *     Lô đã trôi qua trọn khung giờ thì không mở — nó không bao giờ nhận được lượt trả giá nào.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.status = com.nexbid.auction.AuctionStatus.SCHEDULED "
            + "AND a.startTime <= :now AND a.endTime > :now ORDER BY a.startTime")
    List<Auction> findDueToStart(@Param("now") Instant now, Pageable pageable);

    /**
     * EN: Lots whose end time has arrived (guide §26). Locked for the same reason a bid locks: the last
     *     bid and the closing bell must not both think they got there first.
     *     SCHEDULED is included for a lot whose whole window passed while nothing was running.
     * VI: Các lô đã tới giờ đóng (guide §26). Có khoá vì cùng lý do lượt trả giá có khoá: lượt trả giá cuối
     *     và tiếng búa đóng phiên không được cùng nghĩ mình tới trước.
     *     Có cả SCHEDULED, cho lô đã trôi qua trọn khung giờ trong lúc không có gì chạy.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Auction a WHERE a.status IN (com.nexbid.auction.AuctionStatus.ACTIVE, "
            + "com.nexbid.auction.AuctionStatus.SCHEDULED) AND a.endTime <= :now ORDER BY a.endTime")
    List<Auction> findDueToEnd(@Param("now") Instant now, Pageable pageable);

    List<Auction> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    @Query("SELECT a.id FROM Auction a WHERE a.productId = :productId")
    List<UUID> findIdsByProductId(@Param("productId") UUID productId);

    Optional<Auction> findByIdAndSellerId(UUID id, UUID sellerId);

    /**
     * EN: Is this product still committed to an auction? Everything before the close holds it, and so does
     *     a close with a winner; one that ended unsold lets go. Mirrors the partial unique index in V7, which
     *     is the real guard — asking first just turns a constraint violation into a readable error.
     * VI: Sản phẩm này còn bị một phiên giữ không? Mọi trạng thái trước lúc đóng đều giữ, lô đóng có người
     *     thắng cũng giữ; lô kết thúc mà không bán được thì buông. Khớp với index duy nhất có điều kiện ở V7,
     *     vốn là chốt thật — hỏi trước chỉ để biến lỗi ràng buộc thành câu trả lời đọc được.
     */
    @Query("""
            SELECT count(a) > 0 FROM Auction a
            WHERE a.productId = :productId
              AND (a.status IN (com.nexbid.auction.AuctionStatus.DRAFT,
                                com.nexbid.auction.AuctionStatus.PENDING_APPROVAL,
                                com.nexbid.auction.AuctionStatus.SCHEDULED,
                                com.nexbid.auction.AuctionStatus.ACTIVE)
                   OR (a.status = com.nexbid.auction.AuctionStatus.ENDED AND a.winnerId IS NOT NULL))
            """)
    boolean holdsProduct(@Param("productId") UUID productId);

    List<Auction> findByWinnerIdAndStatusInOrderByEndTimeDesc(UUID winnerId, Collection<AuctionStatus> statuses);

    @Query("SELECT a.id FROM Auction a WHERE a.status = com.nexbid.auction.AuctionStatus.SCHEDULED "
            + "AND a.startTime > :from AND a.startTime <= :to")
    List<UUID> findIdsOpeningBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT a.id FROM Auction a WHERE a.status = com.nexbid.auction.AuctionStatus.ACTIVE "
            + "AND a.endTime > :from AND a.endTime <= :to")
    List<UUID> findIdsClosingBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * EN: Oldest first — a review queue should be fair to whoever waited longest.
     * VI: Cũ nhất trước — hàng chờ duyệt nên công bằng với người đợi lâu nhất.
     */
    List<Auction> findByStatusOrderByCreatedAtAsc(AuctionStatus status);
}
