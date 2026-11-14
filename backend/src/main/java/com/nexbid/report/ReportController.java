package com.nexbid.report;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: Reporting needs a signed-in user (SecurityConfig); the queue is under /api/admin/**, so ADMIN only.
 * VI: Báo cáo cần đăng nhập (SecurityConfig); hàng chờ nằm dưới /api/admin/**, nên chỉ ADMIN.
 */
@RestController
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    public record ReportRequest(@NotNull ReportService.Reason reason, @NotBlank @Size(min = 10, max = 1000) String details) {
    }

    public record DismissRequest(@Size(max = 500) String note) {
    }

    public record TakeDownRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @PostMapping("/api/auctions/{auctionId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportService.ReportView> report(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID auctionId, @Valid @RequestBody ReportRequest request) {
        return ApiResponse.of(reports.report(me.id(), auctionId, request.reason(), request.details()), "Report sent");
    }

    @GetMapping("/api/admin/reports")
    public ApiResponse<List<ReportService.ReportView>> list(
            @RequestParam(defaultValue = "OPEN") ReportService.Status status) {
        return ApiResponse.of(reports.list(status));
    }

    @PostMapping("/api/admin/reports/{reportId}/dismiss")
    public ApiResponse<ReportService.ReportView> dismiss(@AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID reportId, @Valid @RequestBody DismissRequest request) {
        return ApiResponse.of(reports.dismiss(admin.id(), reportId, request.note()), "Report dismissed");
    }

    @PostMapping("/api/admin/reports/{reportId}/take-down")
    public ApiResponse<Integer> takeDown(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID reportId,
            @Valid @RequestBody TakeDownRequest request) {
        return ApiResponse.of(reports.takeDown(admin.id(), reportId, request.reason()), "Lot taken down");
    }
}
