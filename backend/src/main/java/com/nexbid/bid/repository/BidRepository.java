package com.nexbid.bid.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.bid.entity.Bid;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    /**
     * EN: Newest first (guide §22). Amount breaks a tie, because two bids saved in the same microsecond
     *     would otherwise come back in whatever order the database felt like.
     * VI: Mới nhất trước (guide §22). Số tiền dùng để phân định khi trùng, vì hai lượt lưu cùng một phần
     *     triệu giây sẽ trả về theo thứ tự tuỳ hứng của database.
     */
    Page<Bid> findByAuctionIdOrderByCreatedAtDescAmountDesc(UUID auctionId, Pageable pageable);

    List<Bid> findByBidderIdOrderByCreatedAtDesc(UUID bidderId);

    @Query("SELECT DISTINCT b.bidderId FROM Bid b WHERE b.auctionId = :auctionId")
    List<UUID> findDistinctBidderIds(@Param("auctionId") UUID auctionId);
}
