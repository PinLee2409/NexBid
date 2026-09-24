package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.product.dto.SaveCategoryRequest;

import jakarta.validation.Valid;

/**
 * EN: Category administration. The ADMIN rule comes from the /api/admin/** pattern set at function 06,
 *     so nothing here repeats it.
 * VI: Quản trị danh mục. Luật ADMIN đến từ mẫu /api/admin/** đặt ở chức năng 06,
 *     nên ở đây không cần khai lại.
 */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categories;

    public AdminCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /** EN: Includes archived ones, unlike the public list. / VI: Gồm cả mục đã lưu trữ, khác danh sách công khai. */
    @GetMapping
    public ApiResponse<List<CategoryView>> list() {
        return ApiResponse.of(categories.listAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryView> create(@Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.of(categories.create(request), "Category created");
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryView> update(
            @PathVariable UUID id, @Valid @RequestBody SaveCategoryRequest request) {
        return ApiResponse.of(categories.update(id, request), "Category updated");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<CategoryView> setStatus(
            @PathVariable UUID id, @RequestBody StatusRequest request) {
        return ApiResponse.of(categories.setStatus(id, request.status()), "Category updated");
    }

    public record StatusRequest(CategoryStatus status) {
    }
}
