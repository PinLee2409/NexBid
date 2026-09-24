package com.nexbid.auction;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * EN: A page of results in the shape the client already expects.
 * VI: Một trang kết quả theo đúng hình dạng client vốn đã chờ.
 *
 * <p>EN: Spring's own Page serialises with an unstable internal structure, and its page numbers start at
 *     zero while the browse URLs start at one. Mapping here keeps both of those out of the contract.
 * <p>VI: Page của Spring serialize ra một cấu trúc nội bộ dễ đổi, và nó đánh số trang từ 0 trong khi URL
 *     duyệt hàng đánh từ 1. Ánh xạ ở đây giữ cả hai thứ đó ngoài hợp đồng API.
 */
public record PageView<T>(List<T> items, int page, int pageSize, long totalItems, int totalPages) {

    public static <E, T> PageView<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageView<>(
                page.getContent().stream().map(mapper).toList(),
                // EN: Back to the one-based numbering the URLs use.
                // VI: Đổi lại cách đánh số từ 1 mà URL đang dùng.
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
