package com.pwaquatics.shop.content.domain;

/**
 * The texts of a gallery photo.
 *
 * @param title optional heading shown in the lightbox
 * @param caption optional sentence or two about the animal
 * @param altText what the photo shows, for screen readers (required)
 */
public record GalleryText(String title, String caption, String altText) {

    public static final int TITLE_MAX = 150;
    public static final int CAPTION_MAX = 500;
    public static final int ALT_TEXT_MAX = 255;

    public GalleryText {
        if (altText == null || altText.isBlank()) {
            throw new IllegalArgumentException("A gallery photo needs an alternative text");
        }
    }
}
