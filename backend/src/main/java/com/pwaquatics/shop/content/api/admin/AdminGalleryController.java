package com.pwaquatics.shop.content.api.admin;

import static com.pwaquatics.shop.common.text.Texts.trimToNull;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.content.application.AdminGalleryService;
import com.pwaquatics.shop.content.application.view.AdminGalleryImageView;
import com.pwaquatics.shop.content.domain.GalleryText;
import com.pwaquatics.shop.security.ShopUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

/** Gallery management (docs/API.md §3.7). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/gallery")
@Tag(name = "Admin: gallery")
class AdminGalleryController {

    private final AdminGalleryService gallery;

    AdminGalleryController(AdminGalleryService gallery) {
        this.gallery = gallery;
    }

    record GalleryImageRequest(
            @Size(max = GalleryText.TITLE_MAX) String title,
            @Size(max = GalleryText.CAPTION_MAX) String caption,
            @NotBlank @Size(max = GalleryText.ALT_TEXT_MAX) String altText,
            boolean published,
            long version) {}

    record GalleryOrderRequest(@NotEmpty @Size(max = AdminGalleryService.MAX_IMAGES) List<@NotNull UUID> imageIds) {}

    @Operation(summary = "Every gallery photo, published or not, in gallery order")
    @GetMapping
    List<AdminGalleryImageView> list() {
        return gallery.list();
    }

    @Operation(summary = "Upload a photo (JPEG, PNG or WebP, at least 400 px wide, max. 15 MB); it starts unpublished")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    AdminGalleryImageView upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("altText") @NotBlank @Size(max = GalleryText.ALT_TEXT_MAX) String altText,
            @RequestParam(name = "title", required = false) @Size(max = GalleryText.TITLE_MAX) String title,
            @RequestParam(name = "caption", required = false) @Size(max = GalleryText.CAPTION_MAX) String caption,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        try {
            return gallery.upload(file.getBytes(), new GalleryText(trimToNull(title), trimToNull(caption),
                    altText.strip()), actor(principal, http));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the upload", e);
        }
    }

    @Operation(summary = "Change a photo's texts and whether it is shown (send the version you loaded)")
    @PutMapping("/{id}")
    AdminGalleryImageView update(
            @PathVariable UUID id,
            @Valid @RequestBody GalleryImageRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return gallery.update(id, new GalleryText(trimToNull(request.title()), trimToNull(request.caption()),
                request.altText().strip()), request.published(), request.version(), actor(principal, http));
    }

    @Operation(summary = "Put the photos in a new order (every photo once, first one first)")
    @PatchMapping("/order")
    List<AdminGalleryImageView> reorder(
            @Valid @RequestBody GalleryOrderRequest request,
            @AuthenticationPrincipal ShopUserPrincipal principal,
            HttpServletRequest http) {
        return gallery.reorder(request.imageIds(), actor(principal, http));
    }

    @Operation(summary = "Delete a photo and its files")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(
            @PathVariable UUID id, @AuthenticationPrincipal ShopUserPrincipal principal, HttpServletRequest http) {
        gallery.delete(id, actor(principal, http));
    }

    private static Actor actor(ShopUserPrincipal principal, HttpServletRequest http) {
        return new Actor(principal.userId(), http.getRemoteAddr());
    }
}
