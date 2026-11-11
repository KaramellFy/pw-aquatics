package com.pwaquatics.shop.catalog.api.admin;

import static com.pwaquatics.shop.catalog.api.admin.AdminTaxonomyController.actor;
import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.catalog.api.admin.AdminProductRequests.AltTextRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminProductRequests.ImageOrderRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminProductRequests.MarkdownPreviewRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminProductRequests.ProductRequest;
import com.pwaquatics.shop.catalog.api.admin.AdminProductRequests.VariantRequest;
import com.pwaquatics.shop.catalog.application.admin.AdminProductQuery;
import com.pwaquatics.shop.catalog.application.admin.AdminProductQuery.AdminProductSort;
import com.pwaquatics.shop.catalog.application.admin.AdminProductQuery.StockFilter;
import com.pwaquatics.shop.catalog.application.admin.AdminProductService;
import com.pwaquatics.shop.catalog.application.admin.ProductInput;
import com.pwaquatics.shop.catalog.application.admin.VariantInput;
import com.pwaquatics.shop.catalog.application.admin.view.AdminProductSummaryView;
import com.pwaquatics.shop.catalog.application.admin.view.AdminProductView;
import com.pwaquatics.shop.catalog.application.admin.view.MarkdownPreviewView;
import com.pwaquatics.shop.catalog.domain.ProductDetails;
import com.pwaquatics.shop.catalog.domain.ProductStatus;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Products, pack sizes and photos (docs/API.md §3.2). Role ADMIN only. Every change answers
 * with the full product, so the editor always shows what was saved.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: products")
class AdminProductController {

    private final AdminProductService products;

    AdminProductController(AdminProductService products) {
        this.products = products;
    }

    @Operation(summary = "All products incl. drafts and archived, with exact stock")
    @GetMapping("/products")
    PageResponse<AdminProductSummaryView> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "ALL") StockFilter stock,
            @RequestParam(defaultValue = "UPDATED_DESC") AdminProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return PageResponse.of(products.list(new AdminProductQuery(q, status, categoryId, stock, sort, page, size)));
    }

    @Operation(summary = "Create a product (unpublished, no stock)")
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView create(
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.create(input(request), actor(principal, http));
    }

    @Operation(summary = "One product with pack sizes, photos and what blocks publishing")
    @GetMapping("/products/{id}")
    AdminProductView get(@PathVariable UUID id) {
        return products.get(id);
    }

    @Operation(summary = "Save the product's details (send the version you loaded)")
    @PutMapping("/products/{id}")
    AdminProductView update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        if (request.version() == null) {
            throw new BusinessRuleException("VERSION_REQUIRED", "The version of the edited product is missing.");
        }
        return products.update(id, input(request), request.version(), actor(principal, http));
    }

    @Operation(summary = "Publish: needs an active pack size, a photo and a tradeable species")
    @PostMapping("/products/{id}/publish")
    AdminProductView publish(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        return products.publish(id, actor(principal, http));
    }

    @Operation(summary = "Archive: removed from the shop, kept for orders and history")
    @PostMapping("/products/{id}/archive")
    AdminProductView archive(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        return products.archive(id, actor(principal, http));
    }

    @Operation(summary = "Copy as a new unpublished product (without stock and photos)")
    @PostMapping("/products/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView duplicate(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        return products.duplicate(id, actor(principal, http));
    }

    @Operation(summary = "Add a pack size")
    @PostMapping("/products/{id}/variants")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView addVariant(
            @PathVariable UUID id,
            @Valid @RequestBody VariantRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.addVariant(id, input(request), actor(principal, http));
    }

    @Operation(summary = "Change a pack size (set active=false to stop selling it)")
    @PutMapping("/products/{id}/variants/{variantId}")
    AdminProductView updateVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId,
            @Valid @RequestBody VariantRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.updateVariant(id, variantId, input(request), actor(principal, http));
    }

    @Operation(summary = "Upload a photo (JPEG, PNG or WebP, at least 400 px wide, max. 15 MB)")
    @PostMapping(path = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductView uploadImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam("altText") @NotBlank @Size(max = 255) String altText,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        try {
            return products.uploadImage(id, file.getBytes(), altText.strip(), actor(principal, http));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the upload", e);
        }
    }

    @Operation(summary = "Change a photo's alternative text")
    @PatchMapping("/products/{id}/images/{imageId}")
    AdminProductView changeAltText(
            @PathVariable UUID id,
            @PathVariable UUID imageId,
            @Valid @RequestBody AltTextRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.changeAltText(id, imageId, request.altText().strip(), actor(principal, http));
    }

    @Operation(summary = "Reorder photos (every photo once; the first is the main photo)")
    @PatchMapping("/products/{id}/images/order")
    AdminProductView reorderImages(
            @PathVariable UUID id,
            @Valid @RequestBody ImageOrderRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.reorderImages(id, request.imageIds(), actor(principal, http));
    }

    @Operation(summary = "Delete a photo")
    @DeleteMapping("/products/{id}/images/{imageId}")
    AdminProductView deleteImage(
            @PathVariable UUID id,
            @PathVariable UUID imageId,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return products.deleteImage(id, imageId, actor(principal, http));
    }

    @Operation(summary = "Render Markdown as the shop will show it")
    @PostMapping("/markdown/preview")
    MarkdownPreviewView previewMarkdown(@Valid @RequestBody MarkdownPreviewRequest request) {
        return products.previewMarkdown(request.markdown());
    }

    private static ProductInput input(ProductRequest r) {
        return new ProductInput(
                new ProductDetails(r.name().strip(), trimToNull(r.slug()), trimToNull(r.shortDescription()),
                        trimToNull(r.description()), trimToNull(r.careNotes()), r.liveAnimal(), r.featured(),
                        r.lowStockThreshold(), r.availableFrom(), r.taxRateBps(), trimToNull(r.seoTitle()),
                        trimToNull(r.seoDescription())),
                r.categoryId(), r.speciesId(), r.gradeId(), r.colourIds() == null ? List.of() : r.colourIds());
    }

    private static VariantInput input(VariantRequest r) {
        return new VariantInput(r.sku(), r.name().strip(), r.packSize(), r.priceCents(), r.compareAtPriceCents(),
                r.active());
    }
}
