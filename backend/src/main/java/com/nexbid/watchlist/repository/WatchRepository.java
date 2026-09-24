package com.nexbid.watchlist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.watchlist.entity.Watch;

public interface WatchRepository extends JpaRepository<Watch, UUID> {

    /**
     * EN: Watches a lot, or does nothing if already watching. One atomic statement, so two taps arriving
     *     together cannot both insert and cannot fail on each other either.
     * VI: Theo dõi một lô, hoặc không làm gì nếu đã theo dõi rồi. Một câu lệnh nguyên tử, nên hai lần bấm
     *     tới cùng lúc không thể cùng thêm dòng, cũng không làm nhau báo lỗi.
     */
    @Modifying
    @Query(value = """
            INSERT INTO watchlists (id, user_id, auction_id, created_at)
            VALUES (gen_random_uuid(), :userId, :auctionId, now())
            ON CONFLICT (user_id, auction_id) DO NOTHING
            """, nativeQuery = true)
    int watch(@Param("userId") UUID userId, @Param("auctionId") UUID auctionId);

    @Modifying
    @Query("DELETE FROM Watch w WHERE w.userId = :userId AND w.auctionId = :auctionId")
    int unwatch(@Param("userId") UUID userId, @Param("auctionId") UUID auctionId);

    List<Watch> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT w.userId FROM Watch w WHERE w.auctionId = :auctionId")
    List<UUID> findUserIdsByAuctionId(@Param("auctionId") UUID auctionId);
}
