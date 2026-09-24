package com.nexbid.product;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;

/**
 * EN: Public browse. No token: someone deciding whether to join must be able to see what is on offer.
 * VI: Duyệt công khai. Không cần token: người đang cân nhắc có tham gia hay không phải xem được hàng có gì.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public ApiResponse<List<CategoryView>> list() {
        return ApiResponse.of(categories.listActive());
    }

    @GetMapping("/{slug}")
    public ApiResponse<CategoryView> bySlug(@PathVariable String slug) {
        return ApiResponse.of(categories.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND, "No category with slug " + slug)));
    }
}
