package com.pwaquatics.shop.media.infrastructure;

import java.util.Map;

/**
 * Where image renditions are kept. Today a local directory served under {@code /media};
 * an S3-compatible store can implement the same interface later.
 */
public interface MediaStorage {

    /**
     * Stores the renditions of one image.
     *
     * @param storageKey e.g. {@code products/<productId>/<imageId>}
     * @param renditions encoded files by name, e.g. {@code 800.webp}
     */
    void store(String storageKey, Map<String, byte[]> renditions);

    /** Removes every file of the image. Missing files are ignored. */
    void delete(String storageKey);
}
