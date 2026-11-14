package com.pwaquatics.shop.content.application;

import com.pwaquatics.shop.content.application.view.GalleryImageView;
import com.pwaquatics.shop.content.domain.GalleryImage;
import com.pwaquatics.shop.content.infrastructure.GalleryImageRepository;
import com.pwaquatics.shop.media.application.MediaUrls;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The public gallery: published photos in the order the owner chose. */
@Service
public class GalleryService {

    private final GalleryImageRepository images;
    private final MediaUrls urls;

    GalleryService(GalleryImageRepository images, MediaUrls urls) {
        this.images = images;
        this.urls = urls;
    }

    @Transactional(readOnly = true)
    public Page<GalleryImageView> published(int page, int size) {
        return images.findByPublishedTrueOrderBySortOrderAscCreatedAtAsc(PageRequest.of(page, size))
                .map(this::view);
    }

    private GalleryImageView view(GalleryImage image) {
        return new GalleryImageView(image.getId(), image.getTitle(), image.getCaption(),
                urls.image(image.getStorageKey(), image.getAltText(), image.getWidth(), image.getHeight()));
    }
}
