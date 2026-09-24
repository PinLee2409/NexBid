package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.product.dto.CreateProductRequest;
import com.nexbid.product.dto.UpdateProductRequest;
import com.nexbid.product.entity.Category;
import com.nexbid.product.entity.Product;
import com.nexbid.product.repository.CategoryRepository;
import com.nexbid.product.repository.ProductImageRepository;
import com.nexbid.product.repository.ProductRepository;
import com.nexbid.product.storage.ImageStorage;

/**
 * EN: Products belong to sellers. Every method here takes the seller id from the caller, never from a payload.
 * VI: Sản phẩm thuộc về người bán. Mọi hàm ở đây nhận id người bán từ bên gọi, không bao giờ từ dữ liệu gửi lên.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ImageStorage storage;

    public ProductService(
            ProductRepository products,
            CategoryRepository categories,
            ProductImageRepository images,
            ImageStorage storage) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.storage = storage;
    }

    @Transactional
    public ProductView create(UUID sellerId, CreateProductRequest request) {
        // EN: An archived category is not a place to file new stock — it is on its way out.
        // VI: Danh mục đã lưu trữ không phải chỗ để xếp hàng mới — nó đang được cho ngừng dùng.
        Category category = activeCategory(request.categoryId());

        ProductStatus status = Boolean.TRUE.equals(request.publishNow())
                ? ProductStatus.AVAILABLE
                : ProductStatus.DRAFT;

        Product product = new Product(
                sellerId,
                category,
                request.name().trim(),
                request.description().trim(),
                request.condition(),
                status);

        return toView(products.save(product));
    }

    /** EN: Only the caller's own products. / VI: Chỉ những sản phẩm của chính người gọi. */
    public List<ProductView> listOwnedBy(UUID sellerId) {
        return products.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(ProductService::toView)
                .toList();
    }

    /**
     * EN: Reads one product the caller owns. A product belonging to someone else answers the same as one
     *     that does not exist — otherwise the endpoint tells you which ids are real.
     * VI: Đọc một sản phẩm của chính người gọi. Sản phẩm của người khác trả lời y như sản phẩm không tồn tại —
     *     nếu không, endpoint này cho biết id nào là có thật.
     */
    public ProductView getOwned(UUID sellerId, UUID productId) {
        return products.findByIdAndSellerId(productId, sellerId)
                .map(ProductService::toView)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "No product with id " + productId));
    }

    /**
     * EN: Product ids in the given categories, for the browse filter. Ids only — the auction module joins
     *     them itself rather than pulling whole products it does not need.
     * VI: Id sản phẩm thuộc các danh mục đã cho, phục vụ bộ lọc duyệt hàng. Chỉ trả id — module auction tự
     *     ghép lấy, thay vì kéo về cả sản phẩm mà nó không cần.
     */
    public List<UUID> idsInCategorySlugs(java.util.Collection<String> slugs) {
        List<UUID> categoryIds = categories.findAll().stream()
                .filter(category -> slugs.contains(category.getSlug()))
                .map(Category::getId)
                .toList();

        return categoryIds.isEmpty() ? List.of() : products.findIdsByCategoryIdIn(categoryIds);
    }

    /**
     * EN: Several products at once, keyed by id — so a list of auctions resolves its items in one query.
     * VI: Lấy nhiều sản phẩm một lần, đánh theo id — để một danh sách phiên phân giải món hàng trong một truy vấn.
     */
    public java.util.Map<UUID, ProductView> findAllById(java.util.Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }

        return products.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, ProductService::toView));
    }

    /**
     * EN: Reads a product without an ownership check. For an admin reviewing a lot, and later for the
     *     public auction page — both need to see a product they do not own.
     * VI: Đọc sản phẩm mà không kiểm quyền sở hữu. Dành cho admin duyệt lô, và sau này cho trang đấu giá
     *     công khai — cả hai đều cần xem sản phẩm không thuộc về mình.
     */
    public java.util.Optional<ProductView> findById(UUID productId) {
        return products.findById(productId).map(ProductService::toView);
    }

    /**
     * EN: Edits a product the caller owns. A lot under the hammer cannot change — bidders are bidding on
     *     what they read, and rewriting it mid-auction would change the deal after the fact.
     * VI: Sửa sản phẩm của chính người gọi. Lô đang đấu giá thì không được đổi — người mua đang trả giá dựa
     *     trên những gì họ đọc, sửa giữa chừng là đổi kèo sau khi họ đã đặt cược.
     */
    @Transactional
    public ProductView update(UUID sellerId, UUID productId, UpdateProductRequest request) {
        Product product = ownedProduct(sellerId, productId);
        requireEditable(product);

        Category category = activeCategory(request.categoryId());

        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description().trim());
        product.setCondition(request.condition());

        if (request.published() != null) {
            product.setStatus(request.published() ? ProductStatus.AVAILABLE : ProductStatus.DRAFT);
        }

        return toView(products.save(product));
    }

    /**
     * EN: Moves a product between the states the auction flow owns. Called by the auction module when a
     *     lot is approved or finishes — a seller can never reach these two states by hand.
     * VI: Chuyển sản phẩm giữa những trạng thái do luồng đấu giá nắm. Module auction gọi khi một lô được
     *     duyệt hoặc kết thúc — người bán không bao giờ tự tay đặt được hai trạng thái này.
     */
    @Transactional
    public void markAuctionState(UUID productId, ProductStatus status) {
        if (status != ProductStatus.IN_AUCTION
                && status != ProductStatus.SOLD
                && status != ProductStatus.AVAILABLE) {

            throw new IllegalArgumentException("Not an auction-owned status: " + status);
        }

        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "No product with id " + productId));

        product.setStatus(status);
        products.save(product);
    }

    /**
     * EN: Removes a product and its photos. Guide §13 forbids deleting one that is in an auction; SOLD is
     *     blocked too, because a completed sale is a record somebody may need to look back at.
     * VI: Xoá sản phẩm và ảnh của nó. Guide §13 cấm xoá sản phẩm đang đấu giá; SOLD cũng bị chặn, vì một
     *     giao dịch đã hoàn tất là bằng chứng có thể còn cần tra lại.
     */
    @Transactional
    public void delete(UUID sellerId, UUID productId) {
        Product product = ownedProduct(sellerId, productId);
        requireEditable(product);

        List<String> urls = images.findUrlsByProductId(productId);

        // EN: Rows first, then the product. The database would cascade anyway, but doing it in this order
        //     keeps JPA's view consistent — otherwise the flush fails on images pointing at a deleted row.
        // VI: Xoá dòng ảnh trước, rồi tới sản phẩm. Database vốn đã cascade, nhưng làm theo thứ tự này giữ
        //     cho JPA nhất quán — nếu không, lúc flush sẽ lỗi vì ảnh còn trỏ vào dòng đã bị xoá.
        images.deleteByProductId(productId);
        images.flush();

        products.delete(product);
        products.flush();

        // EN: Files last. Failing here leaves rubbish on disk, not a broken listing.
        // VI: Xoá file sau cùng. Lỗi ở đây chỉ để lại rác trên đĩa, không làm hỏng tin bán hàng.
        urls.forEach(storage::deleteQuietly);
    }

    private void requireEditable(Product product) {
        if (product.getStatus() == ProductStatus.IN_AUCTION
                || product.getStatus() == ProductStatus.SOLD) {

            throw new BusinessException(
                    ErrorCode.PRODUCT_NOT_EDITABLE,
                    "A product that is in an auction or sold cannot be changed");
        }
    }

    private Category activeCategory(UUID categoryId) {
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND, "No category with id " + categoryId));

        if (category.getStatus() == CategoryStatus.ARCHIVED) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_NOT_FOUND, "This category is archived and cannot take new products");
        }

        return category;
    }

    private Product ownedProduct(UUID sellerId, UUID productId) {
        return products.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "No product with id " + productId));
    }

    private static ProductView toView(Product product) {
        Category category = product.getCategory();

        return new ProductView(
                product.getId(),
                product.getSellerId(),
                new CategoryView(
                        category.getId(),
                        category.getName(),
                        category.getSlug(),
                        category.getDescription(),
                        category.getImageUrl(),
                        category.getStatus()),
                product.getName(),
                product.getDescription(),
                product.getCondition(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
