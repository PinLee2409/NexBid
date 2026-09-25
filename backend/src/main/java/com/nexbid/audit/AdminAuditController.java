package com.nexbid.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auction.PageView;
import com.nexbid.common.response.ApiResponse;

/** EN: The /api/admin/** security rule keeps the ledger private. / VI: Luật /api/admin/** giữ nhật ký riêng cho admin. */
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AuditLogService audit;

    AdminAuditController(AuditLogService audit) {
        this.audit = audit;
    }

    @GetMapping
    public ApiResponse<PageView<AuditLogView>> list(AuditLogQuery query) {
        return ApiResponse.of(audit.list(query));
    }
}
