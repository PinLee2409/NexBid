package com.nexbid.order;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/** EN: A seller's reviews, public like the lot pages that show them. / VI: Đánh giá của người bán, công khai như trang lô hiển thị chúng. */
@RestController
public class SellerReviewController {

    private final ReviewService reviews;

    SellerReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/api/sellers/{id}/reviews")
    public ApiResponse<SellerReviewsView> reviews(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.of(reviews.ofSeller(id, page, size));
    }
}
