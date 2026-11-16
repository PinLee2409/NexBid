package com.nexbid.product;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;

/**
 * EN: The queue of photos and videos no admin has looked at yet, newest first: each new upload lands here. Keeping one
 *     marks it looked at; taking one down removes the file, refreshes the lot pages and tells the seller why. Photos
 *     of lots someone reported come first, since they are the likeliest to need it.
 * VI: Hàng chờ ảnh và video chưa admin nào xem, mới nhất trước: mỗi lần tải lên mới đều vào đây. Giữ lại thì đánh dấu
 *     đã xem; gỡ đi thì xoá file, làm mới trang lô và báo người bán lý do. Ảnh của các lô bị báo cáo được xếp trước,
 *     vì chúng dễ cần xử lý nhất.
 */
@Service
public class MediaReviews {

    private final JdbcTemplate jdbc;
    private final ProductImageService images;
    private final ProductService products;
    private final ApplicationEventPublisher events;

    MediaReviews(JdbcTemplate jdbc, ProductImageService images, ProductService products,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.images = images;
        this.products = products;
        this.events = events;
    }

    /** EN: {@code kind} is IMAGE or VIDEO; {@code id} the image's, or the product's for a video. / VI: {@code kind} là IMAGE hoặc VIDEO; {@code id} của ảnh, hoặc của sản phẩm với video. */
    public record Item(String kind, UUID id, UUID productId, String productName, UUID sellerId, String sellerName,
            String url, Instant uploadedAt, boolean reported) {
    }

    /** EN: Something an admin removed, for the seller's notice and the audit log. / VI: Thứ admin đã gỡ, cho thông báo của người bán và nhật ký. */
    public record Removed(String kind, UUID id, UUID productId, UUID sellerId, String productName, String reason,
            UUID adminId, Instant at) {
    }

    @Transactional(readOnly = true)
    public List<Item> queue() {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT 'IMAGE' AS kind, i.id, p.id AS product_id, p.name, p.seller_id, u.full_name, i.image_url AS url,
                           i.created_at AS uploaded_at
                    FROM product_images i JOIN products p ON p.id = i.product_id JOIN users u ON u.id = p.seller_id
                    WHERE i.reviewed_at IS NULL
                    UNION ALL
                    SELECT 'VIDEO', p.id, p.id, p.name, p.seller_id, u.full_name, p.video_url, p.updated_at
                    FROM products p JOIN users u ON u.id = p.seller_id
                    WHERE p.video_url IS NOT NULL AND p.video_reviewed_at IS NULL
                ) m,
                LATERAL (SELECT EXISTS (SELECT 1 FROM lot_reports r JOIN auctions a ON a.id = r.auction_id
                                        WHERE a.product_id = m.product_id) AS reported) rep
                ORDER BY rep.reported DESC, m.uploaded_at DESC
                LIMIT 200
                """, (rs, n) -> new Item(rs.getString("kind"), rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getString("name"), rs.getObject("seller_id", UUID.class),
                rs.getString("full_name"), rs.getString("url"), rs.getTimestamp("uploaded_at").toInstant(),
                rs.getBoolean("reported")));
    }

    /** EN: Looked at, and fine. / VI: Đã xem, và ổn. */
    @Transactional
    public void keep(String kind, UUID id) {
        Object now = Instant.now().atOffset(ZoneOffset.UTC);
        int changed = "VIDEO".equals(kind)
                ? jdbc.update("UPDATE products SET video_reviewed_at = ? WHERE id = ? AND video_url IS NOT NULL", now, id)
                : jdbc.update("UPDATE product_images SET reviewed_at = ? WHERE id = ?", now, id);
        if (changed == 0) {
            throw new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND, "Nothing to review with id " + id);
        }
    }

    /** EN: Taken down, with the reason the seller will read. / VI: Gỡ đi, kèm lý do người bán sẽ đọc. */
    @Transactional
    public void remove(UUID adminId, String kind, UUID id, String reason) {
        Instant now = Instant.now();
        UUID productId;
        UUID sellerId;
        if ("VIDEO".equals(kind)) {
            productId = id;
            sellerId = products.removeVideoAsModerator(id);
        } else {
            productId = images.removeAsModerator(id);
            sellerId = jdbc.queryForObject("SELECT seller_id FROM products WHERE id = ?", UUID.class, productId);
        }
        String name = jdbc.queryForObject("SELECT name FROM products WHERE id = ?", String.class, productId);
        events.publishEvent(new Removed("VIDEO".equals(kind) ? "VIDEO" : "IMAGE", id, productId, sellerId, name,
                reason == null || reason.isBlank() ? null : reason.strip(), adminId, now));
    }
}
