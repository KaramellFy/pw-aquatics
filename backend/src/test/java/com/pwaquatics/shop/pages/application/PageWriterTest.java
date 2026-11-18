package com.pwaquatics.shop.pages.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.media.application.ImageView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PageWriterTest {

    private final PageWriter writer = new PageWriter(null, new ObjectMapper());
    private final ImageView photo = new ImageView(
            "/media/a-800.webp", "/media/a-400.webp 400w, /media/a-800.webp 800w", "Blue \"Dream\"", 800, 600);

    @Test
    void writesPhotosAsTheAppDoes() {
        StringBuilder first = new StringBuilder();
        writer.image(first, photo, "(min-width: 1024px) 55vw, 100vw", true);
        assertThat(first.toString()).isEqualTo("<img src=\"/media/a-800.webp\""
                + " srcset=\"/media/a-400.webp 400w, /media/a-800.webp 800w\""
                + " sizes=\"(min-width: 1024px) 55vw, 100vw\" alt=\"Blue &quot;Dream&quot;\""
                + " width=\"800\" height=\"600\" fetchpriority=\"high\">");

        // Photos further down wait until the visitor scrolls near them.
        StringBuilder later = new StringBuilder();
        writer.image(later, photo, "92vw", false);
        assertThat(later.toString()).endsWith(" loading=\"lazy\">").doesNotContain("fetchpriority");
    }
}
