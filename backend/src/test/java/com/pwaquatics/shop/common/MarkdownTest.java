package com.pwaquatics.shop.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.common.text.Markdown;
import org.junit.jupiter.api.Test;

class MarkdownTest {

    @Test
    void rendersFormattingAndEscapesRawHtml() {
        String html = Markdown.toSafeHtml("**bold** <img src=x onerror=alert(1)> <script>alert(1)</script>");
        assertThat(html).contains("<strong>bold</strong>").doesNotContain("<img").doesNotContain("<script");
    }

    @Test
    void removesDangerousLinksAndHardensSafeOnes() {
        assertThat(Markdown.toSafeHtml("[x](javascript:alert(1))")).doesNotContain("javascript:");
        assertThat(Markdown.toSafeHtml("[care guide](https://example.com)"))
                .contains("href=\"https://example.com\"")
                .contains("rel=\"nofollow noopener noreferrer\"");
    }

    @Test
    void emptyInputGivesNull() {
        assertThat(Markdown.toSafeHtml("  ")).isNull();
        assertThat(Markdown.toSafeHtml(null)).isNull();
    }
}
