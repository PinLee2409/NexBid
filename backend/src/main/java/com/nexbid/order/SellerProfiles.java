package com.nexbid.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;

/**
 * EN: A seller's public page: who they are, their score (overall and per aspect), what they have on sale now and what
 *     they sold lately, and how many people follow them. Blocked accounts and non-sellers have no page.
 * VI: Trang công khai của người bán: họ là ai, điểm (tổng và từng khía cạnh), đang bán gì và gần đây đã bán gì, và bao
 *     nhiêu người theo dõi. Tài khoản bị khoá và người không phải người bán thì không có trang.
 */
@Service
public class SellerProfiles {

    private final UserService users;
    private final AuctionService auctions;
    private final ReviewService reviews;
    private final JdbcTemplate jdbc;
    private final com.nexbid.user.Vacations vacations;

    SellerProfiles(UserService users, AuctionService auctions, ReviewService reviews, JdbcTemplate jdbc,
            com.nexbid.user.Vacations vacations) {
        this.vacations = vacations;
        this.users = users;
        this.auctions = auctions;
        this.reviews = reviews;
        this.jdbc = jdbc;
    }

    public record Profile(UUID id, String displayName, boolean verified, Instant memberSince, int followers,
            SellerReviewsView score, List<AuctionSummaryView> onSale, List<AuctionSummaryView> sold,
            Instant awayUntil, String awayNote) {
    }

    @Transactional(readOnly = true)
    public Profile of(UUID sellerId) {
        UserAccount seller = users.findById(sellerId)
                .filter(account -> account.status() != UserStatus.BLOCKED && account.roles().contains(RoleName.SELLER))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No seller with id " + sellerId));
        List<UUID> soldIds = jdbc.queryForList("""
                SELECT auction_id FROM orders WHERE seller_id = ? AND status IN ('PAID', 'PROCESSING', 'COMPLETED')
                ORDER BY created_at DESC LIMIT 12
                """, UUID.class, sellerId);
        Integer followers = jdbc.queryForObject("SELECT COUNT(*) FROM seller_follows WHERE seller_id = ?",
                Integer.class, sellerId);
        var soldMap = auctions.participantSummariesOf(soldIds);
        com.nexbid.user.Vacations.Vacation away = vacations.of(sellerId, Instant.now());
        return new Profile(seller.id(), seller.fullName(), seller.verified(), seller.createdAt(),
                followers == null ? 0 : followers, reviews.ofSeller(sellerId, 1, 5),
                auctions.publicSummariesOf(auctions.openLotsOf(sellerId)),
                soldIds.stream().map(soldMap::get).filter(java.util.Objects::nonNull).toList(),
                away.until(), away.note());
    }
}
