package com.nexbid.finance;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: The admin's money reports, as JSON for the page and as CSV to download. Under /api/admin/**, so ADMIN only.
 * VI: Báo cáo tiền của admin, dạng JSON cho trang và CSV để tải về. Nằm dưới /api/admin/**, nên chỉ ADMIN.
 */
@RestController
@RequestMapping("/api/admin/finance")
public class FinanceController {

    private final FinanceReports reports;

    public FinanceController(FinanceReports reports) {
        this.reports = reports;
    }

    @GetMapping("/reconciliation")
    public ApiResponse<FinanceReports.Reconciliation> reconciliation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reports.reconcile(from, to));
    }

    @GetMapping("/charges.csv")
    public ResponseEntity<byte[]> charges(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return csv("nexbid-vnpay-" + from + "-" + to + ".csv", FinanceReports.chargeRows(reports.charges(from, to)));
    }

    @GetMapping("/revenue")
    public ApiResponse<FinanceReports.Revenue> revenue(@RequestParam(defaultValue = "12") int months) {
        return ApiResponse.of(reports.revenue(months, Instant.now()));
    }

    @GetMapping("/revenue.csv")
    public ResponseEntity<byte[]> revenueCsv(@RequestParam(defaultValue = "12") int months) {
        return csv("nexbid-revenue.csv", FinanceReports.revenueRows(reports.revenue(months, Instant.now())));
    }

    /**
     * EN: UTF-8 with a byte-order mark, so Excel shows Vietnamese names correctly when the file is opened directly.
     * VI: UTF-8 kèm dấu BOM, để Excel hiện đúng tên tiếng Việt khi mở file trực tiếp.
     */
    static ResponseEntity<byte[]> csv(String filename, List<List<String>> rows) {
        String body = "﻿" + rows.stream()
                .map(row -> row.stream().map(FinanceController::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n")) + "\r\n";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * EN: Quoted when needed; a leading =, +, - or @ is defused so a spreadsheet never runs it as a formula.
     * VI: Đặt trong ngoặc kép khi cần; ký tự đầu =, +, - hoặc @ được vô hiệu để bảng tính không chạy nó như công thức.
     */
    static String cell(String value) {
        String text = value == null || value.equals("null") ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !text.matches("-?\\d+(\\.\\d+)?")) {
            text = "'" + text;
        }
        return text.matches(".*[\",\r\n].*") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\""
                : text;
    }
}
