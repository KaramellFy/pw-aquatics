package com.pwaquatics.shop.pages.application;

import com.pwaquatics.shop.catalog.application.CatalogQueryService;
import com.pwaquatics.shop.catalog.application.view.CatalogSitemap;
import com.pwaquatics.shop.catalog.application.view.SitemapEntry;
import com.pwaquatics.shop.common.web.SiteLinks;
import com.pwaquatics.shop.pages.application.ShellSource.ShellUnavailableException;
import jakarta.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The files search engines read besides the pages (docs/adr/013-pages-filled-in-by-the-backend.md):
 * {@code /sitemap.xml} lists every public page in each language it is written in, and
 * {@code /robots.txt} is the frontend build's file plus the full link to the sitemap, which only
 * the backend knows ({@code app.base-url}). The frontend's Caddy asks for them as
 * {@code /_site/sitemap.xml} and {@code /_site/robots.txt}.
 */
@Service
public class CrawlerFiles {

    /** The app's indexable pages without catalog data; written in every language. */
    static final List<String> FIXED_PAGES = List.of("/", "/shop", "/gallery", "/about", "/contact");

    private final CatalogQueryService catalog;
    private final ShellSource shells;
    private final SiteLinks links;

    public CrawlerFiles(CatalogQueryService catalog, ShellSource shells, SiteLinks links) {
        this.catalog = catalog;
        this.shells = shells;
        this.links = links;
    }

    /**
     * The sitemap: one entry per page and language, each naming the page in all its languages
     * ({@code hreflang}, English as {@code x-default}), as the pages' own head does. A German page
     * whose texts are not translated yet is left out, because it is not indexed.
     */
    public String sitemap() {
        CatalogSitemap pages = catalog.sitemap();
        StringBuilder xml = new StringBuilder(16_384)
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
                .append(" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");
        FIXED_PAGES.forEach(path -> page(xml, path, PageHead.ALL_LANGUAGES, null));
        pages.categories().forEach(entry -> page(xml, "/shop/" + entry.slug(), entry));
        pages.species().forEach(entry -> page(xml, "/species/" + entry.slug(), entry));
        pages.products().forEach(entry -> page(xml, "/products/" + entry.slug(), entry));
        return xml.append("</urlset>\n").toString();
    }

    /**
     * The build's {@code robots.txt} with the sitemap's link.
     *
     * @throws ShellUnavailableException without a frontend build (or one without robots rules);
     *     the frontend then serves its own file
     */
    public String robots() {
        String rules = shells.current().robots();
        if (rules == null || rules.isBlank()) {
            throw new ShellUnavailableException("The frontend build has no robots.txt rules.");
        }
        return rules.stripTrailing() + "\n\nSitemap: " + links.file("/sitemap.xml") + "\n";
    }

    private void page(StringBuilder xml, String path, SitemapEntry entry) {
        page(xml, path, entry.languages(), entry.lastModified());
    }

    private void page(StringBuilder xml, String path, List<String> languages, @Nullable Instant lastModified) {
        for (String language : PageHead.ALL_LANGUAGES) {
            if (!languages.contains(language)) {
                continue;
            }
            xml.append("  <url>\n    <loc>").append(Html.text(links.page(language, path))).append("</loc>\n");
            if (lastModified != null) {
                xml.append("    <lastmod>").append(lastModified.truncatedTo(ChronoUnit.SECONDS)).append("</lastmod>\n");
            }
            for (String alternate : PageHead.ALL_LANGUAGES) {
                if (languages.contains(alternate)) {
                    alternate(xml, alternate, links.page(alternate, path));
                }
            }
            if (languages.contains("en")) {
                alternate(xml, "x-default", links.page("en", path));
            }
            xml.append("  </url>\n");
        }
    }

    private static void alternate(StringBuilder xml, String language, String href) {
        xml.append("    <xhtml:link rel=\"alternate\" hreflang=\"").append(language)
                .append("\" href=\"").append(Html.attribute(href)).append("\"/>\n");
    }
}
