package com.nexbid.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.product.dto.SaveCategoryRequest;
import com.nexbid.product.entity.Category;
import com.nexbid.product.repository.CategoryRepository;

/**
 * EN: The public API of categories. Entity and repository stay internal to this module.
 * VI: API công khai của danh mục. Entity và repository nằm bên trong module này.
 */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /** EN: What the public browse list shows. / VI: Những gì danh sách duyệt công khai hiển thị. */
    public List<CategoryView> listActive() {
        return categories.findByStatusOrderByNameAsc(CategoryStatus.ACTIVE).stream()
                .map(CategoryService::toView)
                .toList();
    }

    /** EN: Admin sees archived ones too. / VI: Admin thấy cả những mục đã lưu trữ. */
    public List<CategoryView> listAll() {
        return categories.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(CategoryService::toView)
                .toList();
    }

    public Optional<CategoryView> findBySlug(String slug) {
        return categories.findBySlug(slug).map(CategoryService::toView);
    }

    public Optional<CategoryView> findById(UUID id) {
        return categories.findById(id).map(CategoryService::toView);
    }

    @Transactional
    public CategoryView create(SaveCategoryRequest request) {
        String name = request.name().trim();
        String slug = resolveSlug(request.slug(), name);

        if (categories.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS, "A category with this name exists");
        }
        if (categories.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS, "A category with this slug exists");
        }

        return toView(categories.save(new Category(
                name, slug, trimToNull(request.description()), trimToNull(request.imageUrl()))));
    }

    @Transactional
    public CategoryView update(UUID id, SaveCategoryRequest request) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND, "No category with id " + id));

        String name = request.name().trim();
        String slug = resolveSlug(request.slug(), name);

        // EN: Only complain if the clash is with a different row — renaming to itself is fine.
        // VI: Chỉ báo lỗi khi trùng với dòng khác — đổi tên thành chính nó thì không sao.
        categories.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BusinessException(
                            ErrorCode.CATEGORY_ALREADY_EXISTS, "A category with this slug exists");
                });

        category.setName(name);
        category.setSlug(slug);
        category.setDescription(trimToNull(request.description()));
        category.setImageUrl(trimToNull(request.imageUrl()));

        return toView(categories.save(category));
    }

    /**
     * EN: Archiving rather than deleting: products already point at this row, and history must survive.
     * VI: Lưu trữ thay vì xoá: sản phẩm đã trỏ vào dòng này, và lịch sử phải còn nguyên.
     */
    @Transactional
    public CategoryView setStatus(UUID id, CategoryStatus status) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND, "No category with id " + id));

        category.setStatus(status);
        return toView(categories.save(category));
    }

    /**
     * EN: An admin may pin a slug, but a typed one still goes through the same cleaning as a derived one —
     *     otherwise a space or an accent reaches the URL.
     * VI: Admin được tự đặt slug, nhưng slug gõ tay vẫn qua đúng bước làm sạch như slug tự sinh —
     *     nếu không thì dấu cách hoặc dấu tiếng Việt sẽ lọt lên URL.
     */
    private static String resolveSlug(String requested, String name) {
        return Slugs.from(requested == null || requested.isBlank() ? name : requested);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static CategoryView toView(Category category) {
        return new CategoryView(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getStatus());
    }
}
