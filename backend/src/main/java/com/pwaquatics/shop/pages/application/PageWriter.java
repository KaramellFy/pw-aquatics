package com.pwaquatics.shop.pages.application;

import com.pwaquatics.shop.catalog.application.view.ProductSummaryView;
import com.pwaquatics.shop.common.web.SiteLinks;
import com.pwaquatics.shop.media.application.ImageView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the HTML of server-filled pages. Everything that is not sanitised HTML from the API
 * (product descriptions) is escaped here.
 */
@Component
class PageWriter {

    private static final Map<String, String> OG_LOCALES = Map.of("en", "en_GB", "de", "de_DE");

    private final SiteLinks links;
    private final ObjectMapper json;

    PageWriter(SiteLinks links, ObjectMapper json) {
        this.links = links;
        this.json = json;
    }

    /**
     * The head elements of a page, each marked {@code data-page} so the app can remove them
     * when it starts and its own {@code PageMeta} takes over.
     */
    String head(PageHead head, PageContext page) {
        boolean noIndex = head.noIndex() || !head.languages().contains(page.code());
        StringBuilder html = new StringBuilder(1024);
        html.append("<title data-page>").append(Html.text(head.title())).append("</title>");
        if (head.description() != null && !head.description().isBlank()) {
            meta(html, "name", "description", head.description());
        }
        if (noIndex) {
            meta(html, "name", "robots", "noindex");
        } else {
            String own = links.page(page.code(), head.path());
            html.append("<link data-page rel=\"canonical\" href=\"").append(Html.attribute(own)).append("\">");
            for (String language : PageHead.ALL_LANGUAGES) {
                if (head.languages().contains(language)) {
                    alternate(html, language, links.page(language, head.path()));
                }
            }
            if (head.languages().contains("en")) {
                alternate(html, "x-default", links.page("en", head.path()));
            }
            meta(html, "property", "og:url", own);
        }
        meta(html, "property", "og:type", head.type());
        meta(html, "property", "og:site_name", page.text("brand"));
        meta(html, "property", "og:title", head.title());
        if (head.description() != null && !head.description().isBlank()) {
            meta(html, "property", "og:description", head.description());
        }
        meta(html, "property", "og:locale", OG_LOCALES.get(page.code()));
        if (head.image() != null) {
            meta(html, "property", "og:image", links.file(head.image()));
        }
        // Search engines read these from the first page load; the app keeps them until it leaves the page.
        for (Map<String, Object> block : head.structuredData()) {
            html.append("<script data-page type=\"application/ld+json\">")
                    .append(Html.jsonInScript(json.writeValueAsString(block)))
                    .append("</script>");
        }
        return html.toString();
    }

    /** Brand and main navigation, like the app's header. */
    void header(StringBuilder html, PageContext page) {
        html.append("<header class=\"page-preview-header\"><a class=\"page-preview-brand\" href=\"")
                .append(page.link("/")).append("\">").append(Html.text(page.text("brand"))).append("</a>")
                .append("<nav aria-label=\"").append(Html.attribute(page.text("nav.main"))).append("\">");
        for (String item : List.of("shop", "gallery", "about", "contact")) {
            link(html, page.link("/" + item), page.text("nav." + item));
        }
        html.append("</nav></header>");
    }

    /** Links to the pages above this one (the current page is the heading). */
    void breadcrumbs(StringBuilder html, List<Crumb> crumbs) {
        html.append("<ol class=\"page-preview-breadcrumbs\">");
        for (Crumb crumb : crumbs) {
            html.append("<li>");
            link(html, crumb.href(), crumb.label());
            html.append("</li>");
        }
        html.append("</ol>");
    }

    /**
     * Links to the list's previous and next page ("?page=N", the first page without), so search
     * engines that do not run the app find every page of it.
     *
     * @param list the list's path, e.g. "/shop/shrimp"
     * @param current 1-based
     */
    void pagination(StringBuilder html, PageContext page, String list, int current, int totalPages) {
        if (totalPages <= 1) {
            return;
        }
        html.append("<nav class=\"page-preview-pagination\" aria-label=\"")
                .append(Html.attribute(page.text("pagination.label"))).append("\">");
        if (current > 1) {
            String previous = current == 2 ? list : list + "?page=" + (current - 1);
            html.append("<a rel=\"prev\" href=\"").append(Html.attribute(page.link(previous))).append("\">")
                    .append(Html.text(page.text("pagination.previous"))).append("</a>");
        }
        if (current < totalPages) {
            html.append("<a rel=\"next\" href=\"").append(Html.attribute(page.link(list + "?page=" + (current + 1))))
                    .append("\">").append(Html.text(page.text("pagination.next"))).append("</a>");
        }
        html.append("</nav>");
    }

    void heading(StringBuilder html, String heading, String lead) {
        html.append("<h1>").append(Html.text(heading)).append("</h1>");
        paragraph(html, lead);
    }

    void paragraph(StringBuilder html, String text) {
        if (text != null && !text.isBlank()) {
            html.append("<p>").append(Html.text(text)).append("</p>");
        }
    }

    /** Products as links with their price and availability. */
    void products(StringBuilder html, PageContext page, List<ProductSummaryView> products) {
        if (products.isEmpty()) {
            return;
        }
        html.append("<ul class=\"page-preview-products\">");
        for (ProductSummaryView product : products) {
            html.append("<li>");
            link(html, page.link("/products/" + product.slug()), product.name());
            if (product.priceFrom() != null) {
                html.append(" <span>")
                        .append(Html.text(page.texts().format(page.language(), "product.from", "price",
                                page.money(product.priceFrom()))))
                        .append("</span>");
            }
            html.append(" · <span>").append(Html.text(page.text("product.availability." + product.availability())))
                    .append("</span></li>");
        }
        html.append("</ul>");
    }

    /**
     * A photo with its renditions. {@code sizes} must be the app's for the same picture, so the
     * browser picks the same file and the app reuses it; {@code priority} mirrors the app's
     * {@code ResponsiveImage priority} (fetched first; otherwise loaded when scrolled near).
     */
    void image(StringBuilder html, ImageView image, String sizes, boolean priority) {
        html.append("<img src=\"").append(Html.attribute(image.url()))
                .append("\" srcset=\"").append(Html.attribute(image.srcSet()))
                .append("\" sizes=\"").append(Html.attribute(sizes))
                .append("\" alt=\"").append(Html.attribute(image.alt()))
                .append("\" width=\"").append(image.width())
                .append("\" height=\"").append(image.height())
                .append(priority ? "\" fetchpriority=\"high\">" : "\" loading=\"lazy\">");
    }

    void link(StringBuilder html, String href, String label) {
        html.append("<a href=\"").append(Html.attribute(href)).append("\">").append(Html.text(label)).append("</a>");
    }

    private static void meta(StringBuilder html, String kind, String name, String content) {
        html.append("<meta data-page ").append(kind).append("=\"").append(name)
                .append("\" content=\"").append(Html.attribute(content)).append("\">");
    }

    private static void alternate(StringBuilder html, String language, String href) {
        html.append("<link data-page rel=\"alternate\" hreflang=\"").append(language)
                .append("\" href=\"").append(Html.attribute(href)).append("\">");
    }

    record Crumb(String label, String href) {}
}
