package com.nexbid.watchlist.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: One user watching one lot. Rows are inserted by a native statement (see WatchRepository), so this
 *     entity is only ever read.
 * VI: Một người theo dõi một lô. Các dòng được thêm bằng câu lệnh native (xem WatchRepository), nên entity
 *     này chỉ dùng để đọc.
 */
@Entity
@Table(name = "watchlists")
public class Watch {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Watch() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
