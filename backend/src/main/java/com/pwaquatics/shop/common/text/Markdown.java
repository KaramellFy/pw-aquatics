package com.pwaquatics.shop.common.text;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Renders admin-authored Markdown to safe HTML. Raw HTML in the source is escaped and
 * {@code javascript:} style URLs are removed, so the output can be inserted into the page.
 */
public final class Markdown {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory(context -> (node, tagName, attributes) -> {
                if ("a".equals(tagName)) {
                    attributes.put("rel", "nofollow noopener noreferrer");
                }
            })
            .build();

    private Markdown() {}

    /** Returns sanitised HTML, or {@code null} for empty input. */
    public static String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        return RENDERER.render(PARSER.parse(markdown)).strip();
    }
}
