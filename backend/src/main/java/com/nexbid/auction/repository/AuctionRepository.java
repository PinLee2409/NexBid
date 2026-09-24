package com.nexbid.auction.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.entity.Auction;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

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
