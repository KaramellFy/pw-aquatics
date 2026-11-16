package com.pwaquatics.shop.content.domain;

/**
 * The texts of a gallery photo.
 *
 * @param title optional heading shown in the lightbox
 * @param caption optional sentence or two about the animal
 * @param altText what the photo shows, for screen readers (required)
 */
/**
 * The texts of a gallery photo. The {@code …De} texts are the German versions (null where not
 * translated; the English ones are shown).
 */
public record GalleryText(
        String title, String caption, String altText, String titleDe, String captionDe, String altTextDe) {

    public static final int TITLE_MAX = 150;
    public static final int CAPTION_MAX = 500;
    public static final int ALT_TEXT_MAX = 255;

    public GalleryText {
        if (altText == null || altText.isBlank()) {
            throw new IllegalArgumentException("A gallery photo needs an alternative text");
        }
    }

    /** English texts only (a new upload; German ones are added when editing). */
    public static GalleryText english(String title, String caption, String altText) {
        return new GalleryText(title, caption, altText, null, null, null);
    }
}
