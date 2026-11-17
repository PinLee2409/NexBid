package com.nexbid.auction;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Many drafts at once, as after a CSV import: give them all the same opening and closing time, and send them for
 *     approval now or at a set time. Each lot is done on its own; one that cannot (not a draft, its closing time gone)
 *     is reported and the rest go on. A timed submission that fails later leaves the lot a draft.
 * VI: Nhiều lô nháp một lúc, như sau khi nhập CSV: đặt cùng giờ mở và giờ đóng cho tất cả, và gửi duyệt ngay hoặc vào
 *     một thời điểm định trước. Mỗi lô làm riêng; lô nào không làm được (không phải nháp, giờ đóng đã qua) được báo và
 *     các lô khác vẫn chạy. Lần gửi hẹn giờ mà thất bại sau đó thì lô vẫn là nháp.
 */
@Service
public class LotBatch {

    static final int MAX_LOTS = 100;
    private static final Logger log = LoggerFactory.getLogger(LotBatch.class);

    private final AuctionService auctions;
    private final JdbcTemplate jdbc;

    LotBatch(AuctionService auctions, JdbcTemplate jdbc) {
        this.auctions = auctions;
        this.jdbc = jdbc;
    }

    /** EN: Leave start and end out to keep each lot's own times; submitAt null or past submits now. / VI: Bỏ trống giờ mở và đóng để giữ giờ riêng của mỗi lô; submitAt null hoặc đã qua thì gửi ngay. */
    public record Request(List<UUID> auctionIds, Instant startTime, Instant endTime, Instant submitAt, boolean submit) {
    }

    public record Row(UUID auctionId, boolean done, String error) {
    }

    public List<Row> apply(UUID sellerId, Request request, Instant now) {
        List<UUID> ids = request.auctionIds() == null ? List.of() : request.auctionIds().stream().distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_LOTS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Choose between 1 and " + MAX_LOTS + " lots");
        }
        if ((request.startTime() == null) != (request.endTime() == null)) {
            throw new BusinessException(ErrorCode.AUCTION_SCHEDULE_INVALID, "Give both the opening and the closing time");
        }
        boolean later = request.submit() && request.submitAt() != null && request.submitAt().isAfter(now);
        if (later && request.startTime() != null && !request.submitAt().isBefore(request.startTime())) {
            throw new BusinessException(ErrorCode.AUCTION_SCHEDULE_INVALID,
                    "Send them for approval before they are due to open");
        }

        List<Row> rows = new ArrayList<>();
        for (UUID id : ids) {
            try {
                if (request.startTime() != null) {
                    auctions.rescheduleDraft(sellerId, id, request.startTime(), request.endTime());
                }
                if (request.submit() && !later) {
                    auctions.submitForApproval(sellerId, id);
                    jdbc.update("UPDATE auctions SET submit_at = NULL WHERE id = ?", id);
                } else {
                    // EN: Only drafts are kept for later; anything else answers like a missing draft. / VI: Chỉ giữ hẹn cho lô nháp; lô khác trả lời như không có bản nháp.
                    int kept = jdbc.update("""
                            UPDATE auctions SET submit_at = ? WHERE id = ? AND seller_id = ? AND status = 'DRAFT'
                            """, later ? request.submitAt().atOffset(ZoneOffset.UTC) : null, id, sellerId);
                    if (kept == 0) {
                        throw new BusinessException(ErrorCode.AUCTION_NOT_EDITABLE, "Only a draft can be scheduled");
                    }
                }
                rows.add(new Row(id, true, null));
            } catch (BusinessException ex) {
                rows.add(new Row(id, false, ex.getMessage()));
            }
        }
        return rows;
    }

    /**
     * EN: Sends the drafts whose time has come. Run by the auction scheduler; returns how many went.
     * VI: Gửi duyệt các bản nháp đã tới giờ. Do bộ lập lịch phiên chạy; trả về số lô đã gửi.
     */
    public int submitDue(Instant now, int limit) {
        List<Due> due = jdbc.query("""
                SELECT id, seller_id FROM auctions a WHERE status = 'DRAFT' AND submit_at <= ?
                  -- EN: A seller on vacation: the draft waits for their return. / VI: Người bán đang nghỉ: bản nháp chờ họ quay lại.
                  AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = a.seller_id AND u.vacation_until > ?)
                ORDER BY submit_at LIMIT ?
                """, (rs, n) -> new Due(rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class)),
                now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC), limit);
        int sent = 0;
        for (Due lot : due) {
            jdbc.update("UPDATE auctions SET submit_at = NULL WHERE id = ?", lot.id());
            try {
                auctions.submitForApproval(lot.sellerId(), lot.id());
                sent++;
            } catch (BusinessException ex) {
                log.info("Timed submission of lot {} did not go: {}", lot.id(), ex.getMessage());
            }
        }
        return sent;
    }

    private record Due(UUID id, UUID sellerId) {
    }
}
