package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.product.entity.Product;
import com.nexbid.product.entity.ProductImage;
import com.nexbid.product.repository.ProductImageRepository;
import com.nexbid.product.repository.ProductRepository;
import com.nexbid.product.storage.ImageStorage;

/**
 * EN: Images of a product (guide §12). Every method checks the caller owns the product first — that is the
 *     single rule the guide states for this function.
 * VI: Ảnh của sản phẩm (guide §12). Mọi hàm đều kiểm người gọi có sở hữu sản phẩm không trước — đó là luật
 *     duy nhất guide đặt ra cho chức năng này.
 */
@Service
public class ProductImageService {

    /**
     * EN: A listing is a listing, not an album. The cap keeps one seller from filling the disk.
     * VI: Một tin bán hàng là tin bán hàng, không phải album ảnh. Giới hạn này giữ cho một người bán
     *     không làm đầy ổ đĩa.
     */
    private static final int MAX_IMAGES_PER_PRODUCT = 10;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageStorage storage;

    public ProductImageService(
            ProductRepository products, ProductImageRepository images, ImageStorage storage) {
        this.products = products;
        this.images = images;
        this.storage = storage;
    }

    /**
     * EN: Images of any product, with no ownership check — for the admin review and the public lot page.
     * VI: Ảnh của bất kỳ sản phẩm nào, không kiểm quyền sở hữu — dành cho màn admin duyệt và trang lô công khai.
     */
    public List<ProductImageView> listPublic(UUID productId) {
        return toViews(productId);
    }

    public List<ProductImageView> list(UUID sellerId, UUID productId) {
        ownedProduct(sellerId, productId);
        return toViews(productId);
    }

    @Transactional
    public List<ProductImageView> add(
            UUID sellerId, UUID productId, List<MultipartFile> files, String altText) {

        Product product = ownedProduct(sellerId, productId);

        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_INVALID, "No file was uploaded");
        }

        int existing = images.countByProductId(productId);
        if (existing + files.size() > MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessException(
                    ErrorCode.IMAGE_LIMIT_REACHED,
                    "A product may have at most %d images".formatted(MAX_IMAGES_PER_PRODUCT));
        }

        // EN: The first image ever uploaded becomes the cover, because it takes position zero.
        // VI: Tấm ảnh đầu tiên được tải lên thành ảnh bìa, vì nó nhận vị trí số không.
        int nextOrder = existing;

        for (MultipartFile file : files) {
            String url = storage.store(file);
            images.save(new ProductImage(product, url, altText, nextOrder++));
        }

        return toViews(productId);
    }

    /**
     * EN: Removes an image and closes the gap, so positions stay 0,1,2… and the unique index holds.
     * VI: Xoá một ảnh rồi dồn lại, để vị trí luôn là 0,1,2… và ràng buộc duy nhất không bị vi phạm.
     */
    @Transactional
    public List<ProductImageView> remove(UUID sellerId, UUID productId, UUID imageId) {
        ownedProduct(sellerId, productId);

        ProductImage image = images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.IMAGE_NOT_FOUND, "No image with id " + imageId));

        String url = image.getImageUrl();
        images.delete(image);
        images.flush();

        reindex(productId);

        // EN: The row is gone first; the file goes after, and failing to delete it is only untidy.
        // VI: Xoá dòng dữ liệu trước; file xoá sau, và nếu xoá file thất bại thì cũng chỉ là rác thừa.
        storage.deleteQuietly(url);

        return toViews(productId);
    }

    /**
     * EN: Moves one image to the front, which is how a seller picks the cover.
     * VI: Đưa một ảnh lên đầu — đây là cách người bán chọn ảnh bìa.
     */
    @Transactional
    public List<ProductImageView> makeCover(UUID sellerId, UUID productId, UUID imageId) {
        ownedProduct(sellerId, productId);

        ProductImage chosen = images.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.IMAGE_NOT_FOUND, "No image with id " + imageId));

        List<ProductImage> ordered = images.findByProductIdOrderBySortOrderAsc(productId);

        // EN: Shift everything out of the way first. The unique index forbids two rows sharing a position
        //     even for an instant, so negative numbers are used as scratch space.
        // VI: Đẩy tất cả ra chỗ khác trước. Ràng buộc duy nhất cấm hai dòng cùng một vị trí dù chỉ trong
        //     chốc lát, nên dùng số âm làm chỗ trống tạm.
        int scratch = -1;
        for (ProductImage image : ordered) {
            image.setSortOrder(scratch--);
        }
        images.saveAllAndFlush(ordered);

        int order = 0;
        chosen.setSortOrder(order++);
        images.saveAndFlush(chosen);

        for (ProductImage image : ordered) {
            if (!image.getId().equals(imageId)) {
                image.setSortOrder(order++);
                images.saveAndFlush(image);
            }
        }

        return toViews(productId);
    }

    private void reindex(UUID productId) {
        List<ProductImage> ordered = images.findByProductIdOrderBySortOrderAsc(productId);

        int scratch = -1;
        for (ProductImage image : ordered) {
            image.setSortOrder(scratch--);
        }
        images.saveAllAndFlush(ordered);

        int order = 0;
        for (ProductImage image : ordered) {
            image.setSortOrder(order++);
            images.saveAndFlush(image);
        }
    }

    /**
     * EN: A product owned by someone else answers the same as one that does not exist.
     * VI: Sản phẩm của người khác trả lời y như sản phẩm không tồn tại.
     */
    private Product ownedProduct(UUID sellerId, UUID productId) {
        return products.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "No product with id " + productId));
    }

    private List<ProductImageView> toViews(UUID productId) {
        return images.findByProductIdOrderBySortOrderAsc(productId).stream()
                .map(image -> new ProductImageView(
                        image.getId(), image.getImageUrl(), image.getAltText(), image.getSortOrder()))
                .toList();
    }
}
