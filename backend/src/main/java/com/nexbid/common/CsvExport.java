package com.nexbid.common;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * EN: CSV downloads for spreadsheets: UTF-8 with a byte-order mark so Excel shows Vietnamese correctly, CRLF lines,
 *     cells quoted when needed, and a leading =, +, - or @ defused so a spreadsheet never runs it as a formula.
 * VI: Tải CSV cho bảng tính: UTF-8 kèm BOM để Excel hiện đúng tiếng Việt, xuống dòng CRLF, ô đặt trong ngoặc kép khi cần,
 *     và ký tự đầu =, +, - hoặc @ được vô hiệu để bảng tính không chạy nó như công thức.
 */
public final class CsvExport {

    private CsvExport() {
    }

    public static ResponseEntity<byte[]> download(String filename, List<List<String>> rows) {
        String body = "﻿" + rows.stream()
                .map(row -> row.stream().map(CsvExport::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n")) + "\r\n";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    public static String cell(String value) {
        String text = value == null || value.equals("null") ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !text.matches("-?\\d+(\\.\\d+)?")) {
            text = "'" + text;
        }
        return text.matches(".*[\",\r\n].*") || text.contains("\n")
                ? "\"" + text.replace("\"", "\"\"") + "\""
                : text;
    }
}
