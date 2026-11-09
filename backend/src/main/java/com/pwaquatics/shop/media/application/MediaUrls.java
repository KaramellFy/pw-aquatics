package com.pwaquatics.shop.media.application;

import com.pwaquatics.shop.common.config.AppProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds public URLs for stored images. Uploads (Phase 6) store WebP renditions as
 * {@code <storageKey>/<width>.webp}; this class only knows that naming scheme.
 */
@Component
public class MediaUrls {

    /** Rendition widths generated on upload. */
    static final List<Integer> WIDTHS = List.of(400, 800, 1200, 1600);
    private static final int DEFAULT_WIDTH = 800;

    private final String publicPath;

    public MediaUrls(AppProperties properties) {
        this.publicPath = properties.media().publicPath();
    }

    public ImageView image(String storageKey, String alt, int width, int height) {
        List<Integer> widths = WIDTHS.stream().filter(w -> w <= width).toList();
        if (widths.isEmpty()) {
            widths = List.of(WIDTHS.getFirst());
        }
        int defaultWidth = widths.contains(DEFAULT_WIDTH) ? DEFAULT_WIDTH : widths.getLast();
        String srcSet = widths.stream()
                .map(w -> rendition(storageKey, w) + " " + w + "w")
                .collect(Collectors.joining(", "));
        return new ImageView(rendition(storageKey, defaultWidth), srcSet, alt, width, height);
    }

    private String rendition(String storageKey, int width) {
        return publicPath + "/" + storageKey + "/" + width + ".webp";
    }
}
