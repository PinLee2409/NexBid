package com.nexbid.report;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What happened to a report; the notification module tells the reporter.
 * VI: Chuyện gì đã xảy ra với một báo cáo; module notification báo cho người báo cáo.
 */
public final class ReportEvents {

    private ReportEvents() {
    }

    /** EN: {@code outcome} is DISMISSED or REMOVED. / VI: {@code outcome} là DISMISSED hoặc REMOVED. */
    public record Resolved(UUID reportId, UUID auctionId, UUID reporterId, String outcome, String note, UUID actorId,
            Instant at) {
    }
}
