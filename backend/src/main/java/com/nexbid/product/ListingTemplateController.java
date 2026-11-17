package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: A seller's listing templates. Under /api/seller/**, so SELLER only.
 * VI: Mẫu mô tả của người bán. Nằm dưới /api/seller/**, nên chỉ người bán.
 */
@RestController
@RequestMapping("/api/seller/templates")
public class ListingTemplateController {

    private final ListingTemplates templates;

    ListingTemplateController(ListingTemplates templates) {
        this.templates = templates;
    }

    public record TemplateRequest(
            @NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name,
            @NotBlank(message = "Description is required") @Size(max = 5000, message = "Description must be at most 5000 characters") String description,
            UUID categoryId,
            ProductCondition condition,
            @Valid Parcel parcel) {
    }

    @GetMapping
    public ApiResponse<List<ListingTemplates.Template>> list(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(templates.of(seller.id()));
    }

    @PostMapping
    public ApiResponse<ListingTemplates.Template> create(@AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.of(templates.save(seller.id(), null, request.name(), request.description(),
                request.categoryId(), request.condition(), request.parcel()), "Template saved");
    }

    @PutMapping("/{id}")
    public ApiResponse<ListingTemplates.Template> update(@AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.of(templates.save(seller.id(), id, request.name(), request.description(),
                request.categoryId(), request.condition(), request.parcel()), "Template saved");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {
        templates.delete(seller.id(), id);
        return ApiResponse.of(null, "Template deleted");
    }
}
