package com.nexbid.bid.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.bid.entity.AutoBid;

public interface AutoBidRepository extends JpaRepository<AutoBid, UUID> {

    Optional<AutoBid> findByAuctionIdAndUserId(UUID auctionId, UUID userId);

    /**
     * EN: Strongest first; on an equal ceiling, whoever set theirs first.
     * VI: Mạnh nhất trước; nếu mức trần bằng nhau thì ai đặt trước đứng trước.
     */
    List<AutoBid> findByAuctionIdAndActiveTrueOrderByMaxAmountDescCreatedAtAsc(UUID auctionId);
}
