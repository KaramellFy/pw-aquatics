package com.pwaquatics.shop.content.domain;

import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A photo on the public gallery page. Only published photos are shown, in {@code sortOrder};
 * the files themselves are WebP renditions under {@code storageKey} (see media.ImageService).
 */
@Entity
@Table(name = "gallery_images")
public class GalleryImage extends AbstractEntity {

    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Column(length = GalleryText.TITLE_MAX)
    private String title;

    @Column(length = GalleryText.CAPTION_MAX)
    private String caption;

    @Column(name = "alt_text", nullable = false, length = GalleryText.ALT_TEXT_MAX)
    private String altText;

    @Column(nullable = false, updatable = false)
    private int width;

    @Column(nullable = false, updatable = false)
    private int height;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected GalleryImage() {}

    /** A new, unpublished photo at the end of the gallery. */
    public GalleryImage(
            String storageKey, int width, int height, GalleryText text, int sortOrder, UUID createdBy, Instant now) {
        this.storageKey = storageKey;
        this.width = width;
        this.height = height;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
        this.createdAt = now;
        apply(text, now);
    }

    /** Changes the texts and whether the photo is shown. */
    public void update(GalleryText text, boolean published, Instant now) {
        apply(text, now);
        this.published = published;
    }

    public void moveTo(int sortOrder, Instant now) {
        if (this.sortOrder != sortOrder) {
            this.sortOrder = sortOrder;
            this.updatedAt = now;
        }
    }

    private void apply(GalleryText text, Instant now) {
        this.title = text.title();
        this.caption = text.caption();
        this.altText = text.altText();
        this.updatedAt = now;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getAltText() {
        return altText;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
