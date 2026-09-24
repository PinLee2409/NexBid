package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: Images of the caller's own products (guide §12).
 * VI: Ảnh của sản phẩm thuộc chính người gọi (guide §12).
 */
@RestController
@RequestMapping("/api/seller/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService images;

    public ProductImageController(ProductImageService images) {
        this.images = images;
    }

    @GetMapping
    public ApiResponse<List<ProductImageView>> list(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID productId) {

        return ApiResponse.of(images.list(seller.id(), productId));
    }

    /**
     * EN: Multipart, so several files can arrive in one request — the upload screen sends a whole selection.
     * VI: Dạng multipart, để nhiều file cùng đến trong một request — màn tải ảnh gửi cả lượt chọn.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<ProductImageView>> add(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID productId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "alt", required = false) String alt) {

        return ApiResponse.of(images.add(seller.id(), productId, files, alt), "Images uploaded");
    }

    @DeleteMapping("/{imageId}")
    public ApiResponse<List<ProductImageView>> remove(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {

        return ApiResponse.of(images.remove(seller.id(), productId, imageId), "Image removed");
    }

    @PutMapping("/{imageId}/cover")
    public ApiResponse<List<ProductImageView>> makeCover(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID productId,
            @PathVariable UUID imageId) {

        return ApiResponse.of(images.makeCover(seller.id(), productId, imageId), "Cover updated");
    }
}
