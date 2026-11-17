package com.pwaquatics.shop.pages.application;

import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * The built frontend's {@code index.html}, cut where a page's own head, text and data go, plus
 * the app's routes, the texts of the server-filled pages and the build's {@code robots.txt}
 * rules from {@code pages.json} (docs/adr/013-pages-filled-in-by-the-backend.md).
 *
 * <p>The template must contain {@code <html lang="en">}, {@code </head>},
 * {@code <div id="root"></div>} and {@code </body>} (frontend/index.html says so). Its default
 * {@code <title>} and description are kept for pages that are not filled in and replaced for
 * the others.
 */
public final class PageShell {

    private static final String HTML_TAG = "<html lang=\"en\">";
    private static final String HEAD_END = "</head>";
    private static final String ROOT = "<div id=\"root\"></div>";
    private static final String BODY_END = "</body>";
    private static final Pattern DEFAULT_TITLE = Pattern.compile("\\s*<title>[^<]*</title>");
    private static final Pattern DEFAULT_DESCRIPTION =
            Pattern.compile("\\s*<meta\\s+name=\"description\"\\s+content=\"[^\"]*\"\\s*/?>");

    private final String beforeHtmlTag;
    private final String head;
    private final String beforeRoot;
    private final String beforeBodyEnd;
    private final String rest;
    private final String defaultHead;
    private final PageTexts texts;
    private final AppRoutes routes;
    private final @Nullable String robots;

    private PageShell(String html, PageTexts texts, AppRoutes routes, @Nullable String robots) {
        this.texts = Objects.requireNonNull(texts, "texts");
        this.routes = Objects.requireNonNull(routes, "routes");
        this.robots = robots;
        int htmlTag = indexOf(html, HTML_TAG, 0);
        int headEnd = indexOf(html, HEAD_END, htmlTag);
        int root = indexOf(html, ROOT, headEnd);
        int bodyEnd = indexOf(html, BODY_END, root);

        String headPart = html.substring(htmlTag + HTML_TAG.length(), headEnd);
        StringBuilder defaults = new StringBuilder();
        headPart = cut(headPart, DEFAULT_TITLE, defaults);
        headPart = cut(headPart, DEFAULT_DESCRIPTION, defaults);

        this.beforeHtmlTag = html.substring(0, htmlTag);
        this.head = headPart;
        this.defaultHead = defaults.toString();
        this.beforeRoot = html.substring(headEnd, root);
        this.beforeBodyEnd = html.substring(root + ROOT.length(), bodyEnd);
        this.rest = html.substring(bodyEnd);
    }

    /**
     * @throws IllegalArgumentException if the template lacks one of the places pages are filled in
     */
    public static PageShell parse(String html, PageTexts texts, AppRoutes routes) {
        return new PageShell(html, texts, routes, null);
    }

    /**
     * @param pages {@code pages.json}: {@code routes}, {@code texts} and {@code robots}
     * @throws IllegalArgumentException if the template lacks one of the places pages are filled in
     */
    public static PageShell parse(String html, JsonNode pages) {
        JsonNode robots = pages.path("robots");
        return new PageShell(html, PageTexts.of(pages.path("texts")), AppRoutes.of(pages.path("routes")),
                robots.isString() ? robots.asString() : null);
    }

    public PageTexts texts() {
        return texts;
    }

    public AppRoutes routes() {
        return routes;
    }

    /** The rules of the build's {@code robots.txt}; null if the build did not include them. */
    public @Nullable String robots() {
        return robots;
    }

    /**
     * The page as served.
     *
     * @param language the page's language code ("en" or "de")
     * @param pageHead the page's head elements, or null for the template's default title and
     *     description
     * @param body the page's text inside the app's root element (replaced when the app starts)
     * @param data the page's API responses as a JSON object, or null
     */
    public String render(String language, String pageHead, String body, String data) {
        StringBuilder html = new StringBuilder(beforeHtmlTag.length() + head.length() + rest.length() + 8192);
        html.append(beforeHtmlTag)
                .append("<html lang=\"").append(Html.attribute(language)).append("\">")
                .append(head)
                .append(pageHead == null ? defaultHead : pageHead)
                .append(beforeRoot)
                .append("<div id=\"root\">").append(body == null ? "" : body).append("</div>")
                .append(beforeBodyEnd);
        if (data != null) {
            html.append("<script type=\"application/json\" id=\"page-data\">")
                    .append(Html.jsonInScript(data))
                    .append("</script>");
        }
        return html.append(rest).toString();
    }

    private static int indexOf(String html, String marker, int from) {
        int index = html.indexOf(marker, from);
        if (index < 0) {
            throw new IllegalArgumentException("The page template has no " + marker);
        }
        return index;
    }

    private static String cut(String head, Pattern pattern, StringBuilder removed) {
        Matcher matcher = pattern.matcher(head);
        if (!matcher.find()) {
            return head;
        }
        removed.append(matcher.group());
        return head.substring(0, matcher.start()) + head.substring(matcher.end());
    }
}
