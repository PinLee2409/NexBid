package com.nexbid.product;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;

/**
 * EN: A seller's saved starting points for new products: the description (with their returns policy, shipping notes and
 *     so on), the category, the condition and the parcel size. Filling a form from one is the frontend's job; nothing
 *     here touches a product.
 * VI: Các điểm bắt đầu đã lưu của người bán cho sản phẩm mới: mô tả (kèm chính sách đổi trả, ghi chú giao hàng...), danh
 *     mục, tình trạng và kích thước kiện hàng. Việc điền form từ mẫu là việc của frontend; ở đây không động tới sản phẩm.
 */
@Service
public class ListingTemplates {

    static final int LIMIT = 20;

    private final JdbcTemplate jdbc;

    ListingTemplates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Template(UUID id, String name, String description, UUID categoryId, ProductCondition condition,
            Parcel parcel, Instant updatedAt) {
    }

    public List<Template> of(UUID sellerId) {
        return jdbc.query("SELECT * FROM listing_templates WHERE seller_id = ? ORDER BY name",
                (rs, n) -> template(rs), sellerId);
    }

    @Transactional
    public Template save(UUID sellerId, UUID id, String name, String description, UUID categoryId,
            ProductCondition condition, Parcel parcel) {
        String title = name.strip();
        Instant now = Instant.now();
        Object at = now.atOffset(ZoneOffset.UTC);
        Integer weight = parcel == null ? null : parcel.weightGrams();
        Integer length = parcel == null ? null : parcel.lengthCm();
        Integer width = parcel == null ? null : parcel.widthCm();
        Integer height = parcel == null ? null : parcel.heightCm();
        String kind = condition == null ? null : condition.name();
        try {
            if (id == null) {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM listing_templates WHERE seller_id = ?",
                        Integer.class, sellerId);
                if (count != null && count >= LIMIT) {
                    throw new BusinessException(ErrorCode.TEMPLATE_INVALID, "You can keep at most " + LIMIT + " templates");
                }
                id = UUID.randomUUID();
                jdbc.update("""
                        INSERT INTO listing_templates (id, seller_id, name, description, category_id, condition, weight_g,
                            length_cm, width_cm, height_cm, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, sellerId, title, description, categoryId, kind, weight, length, width, height, at, at);
            } else {
                int changed = jdbc.update("""
                        UPDATE listing_templates SET name = ?, description = ?, category_id = ?, condition = ?,
                            weight_g = ?, length_cm = ?, width_cm = ?, height_cm = ?, updated_at = ?
                        WHERE id = ? AND seller_id = ?
                        """, title, description, categoryId, kind, weight, length, width, height, at, id, sellerId);
                if (changed == 0) {
                    throw new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "No template " + id);
                }
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.TEMPLATE_INVALID, "You already have a template named " + title);
        }
        UUID saved = id;
        return jdbc.query("SELECT * FROM listing_templates WHERE id = ?", (rs, n) -> template(rs), saved).getFirst();
    }

    public void delete(UUID sellerId, UUID id) {
        if (jdbc.update("DELETE FROM listing_templates WHERE id = ? AND seller_id = ?", id, sellerId) == 0) {
            throw new ResourceNotFoundException(ErrorCode.TEMPLATE_NOT_FOUND, "No template " + id);
        }
    }

    private static Template template(ResultSet rs) throws SQLException {
        Integer weight = (Integer) rs.getObject("weight_g");
        Parcel parcel = weight == null ? null : new Parcel(weight, (Integer) rs.getObject("length_cm"),
                (Integer) rs.getObject("width_cm"), (Integer) rs.getObject("height_cm"));
        String condition = rs.getString("condition");
        return new Template(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                rs.getObject("category_id", UUID.class), condition == null ? null : ProductCondition.valueOf(condition),
                parcel, rs.getTimestamp("updated_at").toInstant());
    }
}
