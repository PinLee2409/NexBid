package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.product.dto.CreateProductRequest;
import com.nexbid.product.dto.UpdateProductRequest;

import jakarta.validation.Valid;

/**
 * EN: A seller's own products (guide §11). The SELLER rule comes from the /api/seller/** pattern set at
 *     function 06; what this class adds is that a seller only ever sees their own rows.
 * VI: Sản phẩm của chính người bán (guide §11). Luật SELLER đến từ mẫu /api/seller/** đặt ở chức năng 06;
 *     phần lớp này thêm vào là người bán chỉ nhìn thấy đúng dòng của mình.
 */
@RestController
@RequestMapping("/api/seller/products")
public class SellerProductController {

    private final ProductService products;

    public SellerProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductView> create(
            @AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody CreateProductRequest request) {

        return ApiResponse.of(products.create(seller.id(), request), "Product created");
    }

    @GetMapping
    public ApiResponse<List<ProductView>> mine(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(products.listOwnedBy(seller.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductView> one(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        return ApiResponse.of(products.getOwned(seller.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductView> update(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {

        return ApiResponse.of(products.update(seller.id(), id, request), "Product updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        products.delete(seller.id(), id);
        return ApiResponse.ok("Product deleted");
    }
}
