package com.pwaquatics.shop.content.application;

import com.pwaquatics.shop.audit.application.Actor;
import com.pwaquatics.shop.audit.application.AuditService;
import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.content.application.view.AdminGalleryImageView;
import com.pwaquatics.shop.content.domain.GalleryImage;
import com.pwaquatics.shop.content.domain.GalleryText;
import com.pwaquatics.shop.content.infrastructure.GalleryImageRepository;
import com.pwaquatics.shop.media.application.ImageService;
import com.pwaquatics.shop.media.application.MediaUrls;
import com.pwaquatics.shop.media.application.StoredImage;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gallery management (docs/API.md §3.7): upload photos, write their texts, publish them and
 * put them in order. Photos go through {@link ImageService} and are never stored as uploaded.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminGalleryService {

    /** Enough for a well-stocked gallery; keeps the admin list and the reorder request small. */
    public static final int MAX_IMAGES = 200;

    private final GalleryImageRepository images;
    private final ImageService imageService;
    private final MediaUrls urls;
    private final AuditService audit;
    private final Clock clock;

    AdminGalleryService(
            GalleryImageRepository images, ImageService imageService, MediaUrls urls, AuditService audit, Clock clock) {
        this.images = images;
        this.imageService = imageService;
        this.urls = urls;
        this.audit = audit;
        this.clock = clock;
    }

    /** Every photo, published or not, in gallery order. */
    @Transactional(readOnly = true)
    public List<AdminGalleryImageView> list() {
        return images.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(this::view).toList();
    }

    /**
     * Processes and stores a photo as a new, unpublished image at the end of the gallery.
     *
     * @throws BusinessRuleException {@code IMAGE_LIMIT_REACHED}, or the image codes of
     *     {@link ImageService} if the file is not a usable photo
     */
    @Transactional
    public AdminGalleryImageView upload(byte[] bytes, GalleryText text, Actor actor) {
        List<GalleryImage> all = images.lockAll();
        if (all.size() >= MAX_IMAGES) {
            throw new BusinessRuleException(
                    "IMAGE_LIMIT_REACHED", "The gallery can hold up to " + MAX_IMAGES + " photos.");
        }
        StoredImage stored = imageService.storeGalleryImage(bytes);
        int sortOrder = all.stream().mapToInt(GalleryImage::getSortOrder).max().orElse(-1) + 1;
        GalleryImage image = images.save(new GalleryImage(stored.storageKey(), stored.width(), stored.height(), text,
                sortOrder, actor.userId(), clock.instant()));
        audit.record(actor, "GALLERY_IMAGE_UPLOADED", "GALLERY_IMAGE", image.getId(), Map.of());
        return view(image);
    }

    /**
     * Changes a photo's texts and whether it is shown.
     *
     * @param version the version the editor loaded
     * @throws ConflictException {@code CONFLICT} if someone else saved the photo in between
     */
    @Transactional
    public AdminGalleryImageView update(UUID id, GalleryText text, boolean published, long version, Actor actor) {
        GalleryImage image = load(id);
        if (image.getVersion() != version) {
            throw new ConflictException(
                    "CONFLICT", "This photo was changed in the meantime. Please reload it and try again.");
        }
        boolean wasPublished = image.isPublished();
        image.update(text, published, clock.instant());
        images.flush();
        audit.record(actor, "GALLERY_IMAGE_UPDATED", "GALLERY_IMAGE", id,
                wasPublished == published ? Map.of() : Map.of("published", published));
        return view(image);
    }

    /**
     * Puts the photos in a new order.
     *
     * @param ids every photo exactly once, first one first
     * @throws BusinessRuleException {@code IMAGE_ORDER_INVALID}
     */
    @Transactional
    public List<AdminGalleryImageView> reorder(List<UUID> ids, Actor actor) {
        List<GalleryImage> all = images.lockAll();
        Map<UUID, GalleryImage> byId = all.stream().collect(Collectors.toMap(GalleryImage::getId, Function.identity()));
        if (ids.size() != all.size() || new HashSet<>(ids).size() != ids.size() || !byId.keySet().containsAll(ids)) {
            throw new BusinessRuleException("IMAGE_ORDER_INVALID", "The new order must list every photo once.");
        }
        Instant now = clock.instant();
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i, now);
        }
        images.flush();
        audit.record(actor, "GALLERY_REORDERED", "GALLERY_IMAGE", null, Map.of("count", ids.size()));
        return ids.stream().map(byId::get).map(this::view).toList();
    }

    /** Deletes a photo; its files are removed once the deletion is committed. */
    @Transactional
    public void delete(UUID id, Actor actor) {
        GalleryImage image = load(id);
        images.delete(image);
        imageService.deleteAfterCommit(image.getStorageKey());
        audit.record(actor, "GALLERY_IMAGE_DELETED", "GALLERY_IMAGE", id, Map.of());
    }

    private GalleryImage load(UUID id) {
        return images.findById(id).orElseThrow(() -> new NotFoundException("Photo not found."));
    }

    private AdminGalleryImageView view(GalleryImage image) {
        return new AdminGalleryImageView(image.getId(), image.getTitle(), image.getCaption(), image.getAltText(),
                image.isPublished(), image.getSortOrder(),
                urls.image(image.getStorageKey(), image.getAltText(), image.getWidth(), image.getHeight()),
                image.getCreatedAt(), image.getVersion());
    }
}
