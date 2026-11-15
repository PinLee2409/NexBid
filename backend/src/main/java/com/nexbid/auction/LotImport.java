package com.nexbid.auction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.nexbid.auction.dto.CreateAuctionRequest;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.product.CategoryService;
import com.nexbid.product.Parcel;
import com.nexbid.product.ProductCondition;
import com.nexbid.product.ProductImageService;
import com.nexbid.product.ProductService;
import com.nexbid.product.ProductView;
import com.nexbid.product.dto.CreateProductRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * EN: Lists many lots from one CSV file plus their photos. Each row becomes a published product with its photos and a
 *     DRAFT lot, in a transaction of its own: a bad row is reported and skipped, the others go through. Nothing is
 *     submitted for approval; the seller checks the drafts first. Times without a zone are Vietnam time.
 * VI: Đăng nhiều lô từ một file CSV kèm ảnh. Mỗi dòng thành một sản phẩm đã đăng kèm ảnh và một lô NHÁP, trong
 *     transaction riêng: dòng lỗi được báo và bỏ qua, các dòng khác vẫn qua. Không gửi duyệt gì cả; người bán kiểm tra
 *     các bản nháp trước. Giờ không ghi múi giờ được hiểu là giờ Việt Nam.
 */
@Service
public class LotImport {

    static final int MAX_ROWS = 50;
    static final List<String> COLUMNS = List.of("name", "description", "category", "condition", "starting_price",
            "increment", "buy_now_price", "reserve_price", "start", "end", "images", "weight_g", "length_cm",
            "width_cm", "height_cm");
    private static final Set<String> REQUIRED = Set.of("name", "description", "category", "condition",
            "starting_price", "increment", "start", "end");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]");

    private final ProductService products;
    private final ProductImageService images;
    private final CategoryService categories;
    private final AuctionService auctions;
    private final Validator validator;
    private final TransactionTemplate perRow;

    LotImport(ProductService products, ProductImageService images, CategoryService categories,
            AuctionService auctions, Validator validator, PlatformTransactionManager transactions) {
        this.products = products;
        this.images = images;
        this.categories = categories;
        this.auctions = auctions;
        this.validator = validator;
        this.perRow = new TransactionTemplate(transactions);
    }

    /** EN: What happened to one row; {@code line} counts the header as line 1. / VI: Chuyện gì xảy ra với một dòng; {@code line} tính dòng tiêu đề là dòng 1. */
    public record RowResult(int line, String name, UUID auctionId, List<String> errors) {
    }

    public record Result(int created, int failed, List<RowResult> rows) {
    }

    public Result run(UUID sellerId, MultipartFile csv, List<MultipartFile> photos) {
        List<List<String>> table = parse(text(csv));
        if (table.isEmpty()) {
            throw invalid("The file is empty");
        }
        List<String> header = table.getFirst().stream().map(cell -> cell.trim().toLowerCase(Locale.ROOT)).toList();
        for (String column : REQUIRED) {
            if (!header.contains(column)) {
                throw invalid("Missing column: " + column);
            }
        }
        List<List<String>> rows = table.subList(1, table.size()).stream()
                .filter(row -> row.stream().anyMatch(cell -> !cell.isBlank())).toList();
        if (rows.isEmpty()) {
            throw invalid("The file has no rows");
        }
        if (rows.size() > MAX_ROWS) {
            throw invalid("At most " + MAX_ROWS + " rows at a time");
        }
        Map<String, MultipartFile> byName = new HashMap<>();
        for (MultipartFile photo : photos == null ? List.<MultipartFile>of() : photos) {
            if (photo.getOriginalFilename() != null) {
                byName.put(photo.getOriginalFilename().trim().toLowerCase(Locale.ROOT), photo);
            }
        }

        List<RowResult> results = new ArrayList<>();
        int line = 1;
        for (List<String> cells : rows) {
            line++;
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), i < cells.size() ? cells.get(i).trim() : "");
            }
            results.add(importRow(sellerId, line, row, byName));
        }
        int created = (int) results.stream().filter(result -> result.auctionId() != null).count();
        return new Result(created, results.size() - created, results);
    }

    private RowResult importRow(UUID sellerId, int line, Map<String, String> row, Map<String, MultipartFile> photos) {
        String name = row.getOrDefault("name", "");
        List<String> errors = new ArrayList<>();
        UUID categoryId = categories.findBySlug(row.getOrDefault("category", ""))
                .map(found -> found.id()).orElse(null);
        if (categoryId == null) {
            errors.add("category: no category with slug \"" + row.get("category") + "\"");
        }
        ProductCondition condition = parseEnum(row.get("condition"), errors);
        BigDecimal starting = money(row, "starting_price", errors);
        BigDecimal increment = money(row, "increment", errors);
        BigDecimal buyNow = money(row, "buy_now_price", errors);
        BigDecimal reserve = money(row, "reserve_price", errors);
        Instant start = time(row, "start", errors);
        Instant end = time(row, "end", errors);
        Parcel parcel = parcel(row, errors);
        List<MultipartFile> files = new ArrayList<>();
        for (String file : row.getOrDefault("images", "").split("\\|")) {
            if (file.isBlank()) {
                continue;
            }
            MultipartFile found = photos.get(file.trim().toLowerCase(Locale.ROOT));
            if (found == null) {
                errors.add("images: \"" + file.trim() + "\" was not uploaded");
            } else {
                files.add(found);
            }
        }

        CreateProductRequest product = new CreateProductRequest(name, row.getOrDefault("description", ""), categoryId,
                condition, true, parcel);
        violations(validator.validate(product), errors);
        CreateAuctionRequest lot = new CreateAuctionRequest(UUID.randomUUID(), starting, increment, start, end, true,
                null, null, reserve, buyNow);
        violations(validator.validate(lot), errors);
        if (!errors.isEmpty()) {
            return new RowResult(line, name, null, errors);
        }

        try {
            UUID auctionId = perRow.execute(status -> {
                ProductView created = products.create(sellerId, product);
                if (!files.isEmpty()) {
                    images.add(sellerId, created.id(), files, created.name());
                }
                return auctions.create(sellerId, new CreateAuctionRequest(created.id(), starting, increment, start, end,
                        true, null, null, reserve, buyNow)).id();
            });
            return new RowResult(line, name, auctionId, List.of());
        } catch (BusinessException ex) {
            return new RowResult(line, name, null, List.of(ex.getMessage()));
        }
    }

    private static void violations(Set<? extends ConstraintViolation<?>> found, List<String> errors) {
        for (ConstraintViolation<?> violation : found) {
            String field = violation.getPropertyPath().toString();
            if (!field.equals("productId")) {
                errors.add(field + ": " + violation.getMessage());
            }
        }
    }

    private static ProductCondition parseEnum(String value, List<String> errors) {
        try {
            return ProductCondition.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException ex) {
            errors.add("condition: one of NEW, LIKE_NEW, GOOD, FAIR");
            return null;
        }
    }

    private static BigDecimal money(Map<String, String> row, String column, List<String> errors) {
        String value = row.getOrDefault(column, "").replace("₫", "").replace(" ", "").trim();
        if (value.isEmpty()) {
            return null;
        }
        // EN: Whole đồng, thousands grouped with "." or "," or not at all: 1500000, 1.500.000, 1,500,000.
        // VI: Số đồng nguyên, nhóm nghìn bằng "." hoặc "," hoặc không nhóm: 1500000, 1.500.000, 1,500,000.
        if (value.matches("\\d{1,3}([.,]\\d{3})+") || value.matches("\\d+")) {
            return new BigDecimal(value.replace(".", "").replace(",", ""));
        }
        errors.add(column + ": a whole number of đồng, e.g. 1500000 or 1.500.000");
        return null;
    }

    private static Instant time(Map<String, String> row, String column, List<String> errors) {
        String value = row.getOrDefault(column, "").trim();
        try {
            return value.matches(".*([zZ]|[+-]\\d\\d:?\\d\\d)$")
                    ? OffsetDateTime.parse(value).toInstant()
                    : LocalDateTime.parse(value, LOCAL).atZone(VIETNAM).toInstant();
        } catch (DateTimeParseException ex) {
            errors.add(column + ": use yyyy-MM-dd HH:mm (Vietnam time)");
            return null;
        }
    }

    private static Parcel parcel(Map<String, String> row, List<String> errors) {
        List<String> values = List.of("weight_g", "length_cm", "width_cm", "height_cm").stream()
                .map(column -> row.getOrDefault(column, "").trim()).toList();
        if (values.stream().allMatch(String::isEmpty)) {
            return null;
        }
        try {
            return new Parcel(Integer.valueOf(values.get(0)), Integer.valueOf(values.get(1)),
                    Integer.valueOf(values.get(2)), Integer.valueOf(values.get(3)));
        } catch (NumberFormatException ex) {
            errors.add("parcel: fill weight_g, length_cm, width_cm and height_cm with whole numbers, or none");
            return null;
        }
    }

    private static String text(MultipartFile csv) {
        if (csv == null || csv.isEmpty()) {
            throw invalid("Choose a CSV file");
        }
        try {
            String text = new String(csv.getBytes(), StandardCharsets.UTF_8);
            // EN: Excel saves UTF-8 with a byte-order mark. / VI: Excel lưu UTF-8 kèm dấu BOM.
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (java.io.IOException ex) {
            throw invalid("The file could not be read");
        }
    }

    /**
     * EN: RFC 4180: commas or semicolons (Excel in Vietnamese locales) between fields, quotes around fields that hold
     *     them or line breaks, "" for a quote inside quotes.
     * VI: RFC 4180: dấu phẩy hoặc chấm phẩy (Excel ở locale tiếng Việt) giữa các trường, dấu ngoặc kép quanh trường có
     *     chứa chúng hoặc xuống dòng, "" cho dấu ngoặc kép bên trong.
     */
    static List<List<String>> parse(String text) {
        int firstBreak = text.indexOf('\n');
        String firstLine = firstBreak < 0 ? text : text.substring(0, firstBreak);
        char separator = firstLine.chars().filter(c -> c == ';').count() > firstLine.chars().filter(c -> c == ',').count()
                ? ';' : ',';
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == separator) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.IMPORT_INVALID, message);
    }
}
