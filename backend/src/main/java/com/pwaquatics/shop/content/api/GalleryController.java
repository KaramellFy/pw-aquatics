package com.pwaquatics.shop.content.api;

import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.content.application.GalleryService;
import com.pwaquatics.shop.content.application.view.GalleryImageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Content")
class GalleryController {

    private final GalleryService gallery;

    GalleryController(GalleryService gallery) {
        this.gallery = gallery;
    }

    @Operation(summary = "Published gallery photos, in the shop's order")
    @GetMapping("/api/v1/gallery")
    PageResponse<GalleryImageView> gallery(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "24") @Min(1) @Max(48) int size) {
        return PageResponse.of(gallery.published(page, size));
    }
}
