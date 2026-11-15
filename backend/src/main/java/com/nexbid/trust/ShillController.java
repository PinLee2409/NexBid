package com.nexbid.trust;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: The admin's review queue for suspected shill bidding. Under /api/admin/**, so ADMIN only.
 * VI: Hàng chờ admin xem xét nghi vấn tự trả giá. Nằm dưới /api/admin/**, nên chỉ ADMIN.
 */
@RestController
public class ShillController {

    private final ShillSignals signals;

    public ShillController(ShillSignals signals) {
        this.signals = signals;
    }

    @GetMapping("/api/admin/shill-suspects")
    public ApiResponse<List<ShillSignals.Suspect>> suspects() {
        return ApiResponse.of(signals.suspects(Instant.now()));
    }

    public record ClearRequest(@NotNull UUID sellerId, @NotNull UUID bidderId,
            @Size(max = 500, message = "Note must be at most 500 characters") String note) {
    }

    @PostMapping("/api/admin/shill-suspects/clear")
    public ApiResponse<Void> clear(@AuthenticationPrincipal CurrentUser admin, @Valid @RequestBody ClearRequest request) {
        signals.clear(admin.id(), request.sellerId(), request.bidderId(), request.note(), Instant.now());
        return ApiResponse.of(null, "Pair cleared");
    }
}
