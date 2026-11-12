package com.nexbid.auction.dto;

import jakarta.validation.constraints.NotNull;

/** EN: true sells to the highest bidder below the reserve; false keeps the lot. / VI: true bán cho người trả cao nhất dưới giá sàn; false giữ lại lô. */
public record ReserveDecisionRequest(

        @NotNull(message = "Say whether to sell")
        Boolean accept) {
}
