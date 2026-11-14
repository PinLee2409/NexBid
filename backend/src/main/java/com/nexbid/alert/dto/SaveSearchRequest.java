package com.nexbid.alert.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexbid.product.ProductCondition;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * EN: The catalogue's filters as they stand when "Save search" is pressed. Status, sort and "ending soon" are left
 *     out: an alert is about lots that have just appeared.
 * VI: Các bộ lọc của danh mục lúc bấm "Lưu tìm kiếm". Trạng thái, sắp xếp và "sắp đóng" được bỏ qua: báo lô mới là về
 *     những lô vừa xuất hiện.
 */
public record SaveSearchRequest(
        @Size(max = 100) String q,
        @Size(max = 20) List<@Size(max = 80) String> category,
        @Size(max = 10) List<ProductCondition> condition,
        @PositiveOrZero BigDecimal minPrice,
        @PositiveOrZero BigDecimal maxPrice) {
}
