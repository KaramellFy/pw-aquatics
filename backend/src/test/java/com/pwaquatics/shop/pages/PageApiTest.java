package com.pwaquatics.shop.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.FakeShellSource;
import com.pwaquatics.shop.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;

/** Pages filled in for the first page load (docs/adr/013-pages-filled-in-by-the-backend.md). */
@IntegrationTest
@Sql(scripts = "/catalog/catalog-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/catalog/catalog-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class PageApiTest {

    private static final String SITE = "http://localhost:5173";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FakeShellSource shells;

    @Test
    void aProductPageCarriesItsHeadTextAndData() throws Exception {
        String html = mvc.perform(get("/_pages/products/alpha-blue"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .startsWith("<!doctype html>\n<html lang=\"en\">")
                .contains("<title data-page>Alpha Blue – PW Aquatics</title>")
                .doesNotContain("<title>PW Aquatics</title>", "Default description.")
                .contains("<meta data-page name=\"description\" content=\"Blue shrimp\">")
                .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/products/alpha-blue\">")
                .contains("<link data-page rel=\"alternate\" hreflang=\"en\" href=\"" + SITE + "/products/alpha-blue\">")
                .contains("hreflang=\"x-default\"")
                // Not written in German yet: no German alternate.
                .doesNotContain("hreflang=\"de\"", "noindex")
                .contains("<meta data-page property=\"og:type\" content=\"product\">")
                // Structured data: the product with one offer per pack size, and the way to it.
                .contains("<script data-page type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"Product\"")
                .contains("\"name\":\"Alpha Blue (Testus visibilis)\"", "\"url\":\"" + SITE + "/products/alpha-blue\"")
                .contains("{\"@type\":\"Offer\",\"name\":\"1 animal\",\"sku\":\"T-ALPHA-1\",\"price\":\"3.00\","
                        + "\"priceCurrency\":\"EUR\",\"availability\":\"https://schema.org/InStock\","
                        + "\"url\":\"" + SITE + "/products/alpha-blue\"}")
                .contains("\"sku\":\"T-ALPHA-10\",\"price\":\"25.00\"")
                .contains("\"@type\":\"BreadcrumbList\",\"itemListElement\":["
                        + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Shop\",\"item\":\"" + SITE + "/shop\"},"
                        + "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"Shrimp\",\"item\":\"" + SITE + "/shop/t-shrimp\"},"
                        + "{\"@type\":\"ListItem\",\"position\":3,\"name\":\"Neocaridina\",\"item\":\"" + SITE + "/shop/t-neo\"},"
                        + "{\"@type\":\"ListItem\",\"position\":4,\"name\":\"Alpha Blue\",\"item\":\"" + SITE + "/products/alpha-blue\"}]")
                .doesNotContain("AggregateOffer")
                // The page's text, inside the app's root element.
                .contains("<div id=\"root\"><div class=\"page-preview\">")
                .contains("<a href=\"/shop/t-shrimp\">Shrimp</a>", "<a href=\"/shop/t-neo\">Neocaridina</a>")
                .contains("<h1>Alpha Blue</h1>", "<p><i>Testus visibilis</i></p>")
                .contains("<li>1 animal: €3.00</li>", "<li>10 animals: €25.00</li>", "<p>In stock</p>")
                .contains("<strong>blue</strong>")
                // The owner's Markdown is sanitised: the script and the javascript: link stay text.
                .doesNotContain("<script>alert(1)", "javascript:")
                // The API response the app asks for first.
                .contains("<script type=\"application/json\" id=\"page-data\">{\"/products/alpha-blue\":{")
                .contains("<script type=\"module\" crossorigin src=\"/assets/index-abc.js\"></script>");
    }

    @Test
    void germanPagesUseGermanTextsAndStayOutOfTheIndexUntilTranslated() throws Exception {
        jdbc.update("UPDATE products SET name_de = 'Alpha Blau', description_de = 'Tiefes Blau.' WHERE slug = 'alpha-blue'");
        try {
            String translated = page("/_pages/de/products/alpha-blue");
            assertThat(translated)
                    .contains("<html lang=\"de\">", "<title data-page>Alpha Blau – PW Aquatics</title>")
                    .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/de/products/alpha-blue\">")
                    .contains("<link data-page rel=\"alternate\" hreflang=\"de\" href=\"" + SITE + "/de/products/alpha-blue\">")
                    .contains("<meta data-page property=\"og:locale\" content=\"de_DE\">")
                    .contains("<a href=\"/de/shop\">Shop</a>", "<a href=\"/de/gallery\">Galerie</a>")
                    .contains("<li>1 animal: 3,00\u00a0€</li>", "<p>Auf Lager</p>", "<p>Tiefes Blau.</p>")
                    .doesNotContain("noindex");

            // English text on a German link: shown, but not offered to search engines.
            assertThat(page("/_pages/de/products/beta-red"))
                    .contains("<meta data-page name=\"robots\" content=\"noindex\">", "<h1>Beta Red</h1>")
                    .doesNotContain("rel=\"canonical\"", "rel=\"alternate\"");
            // The English page now names its German version.
            assertThat(page("/_pages/products/alpha-blue")).contains("hreflang=\"de\"");
        } finally {
            jdbc.update("UPDATE products SET name_de = NULL, description_de = NULL WHERE slug = 'alpha-blue'");
        }
    }

    @Test
    void pagesAndHealthAnswerHeadRequestsLikeGet() throws Exception {
        // Uptime monitors and link checkers often ask with HEAD.
        mvc.perform(head("/_pages/products/alpha-blue"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"));
        mvc.perform(head("/_pages/products/no-such-animal")).andExpect(status().isNotFound());
        mvc.perform(head("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(head("/api/v1/shop-info")).andExpect(status().isOk());
        // HEAD does not open anything that GET keeps closed.
        mvc.perform(head("/api/v1/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownProductsSpeciesAndCategoriesAnswerNotFound() throws Exception {
        for (String path : new String[] {
            "/_pages/products/no-such-animal", "/_pages/products/delta-draft", "/_pages/products/Not_A_Slug",
            "/_pages/de/shop/no-such-category", "/_pages/species/t-banned",
        }) {
            String html = mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(path)
                    .contains("<meta data-page name=\"robots\" content=\"noindex\">")
                    .contains("<title data-page>Page not found – PW Aquatics</title>")
                    .doesNotContain("rel=\"canonical\"");
        }
    }

    @Test
    void shopAndSpeciesPagesListTheirAnimals() throws Exception {
        assertThat(page("/_pages/shop/t-shrimp"))
                .contains("<title data-page>Shrimp – PW Aquatics</title>")
                .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/shop/t-shrimp\">")
                .contains("<h1>Shrimp</h1>", "<a href=\"/shop/t-neo\">Neocaridina</a>")
                .contains("<a href=\"/products/alpha-blue\">Alpha Blue</a> <span>from €3.00</span> · <span>In stock</span>")
                .contains("<a href=\"/products/beta-red\">Beta Red</a>")
                .doesNotContain("Gamma Snail")
                .contains("\"/categories\":[", "\"/products?category=t-shrimp&size=24\":{\"items\":[")
                // The filter panel's data, so the shop needs no request before it can be used.
                .contains("\"/products/facets?category=t-shrimp\":{", "\"/species\":[", "\"/colours\":[",
                        "\"/grades\":[");
        assertThat(page("/_pages/shop")).contains("\"/products/facets\":{");
        assertThat(page("/_pages/gallery")).contains("\"/gallery?page=0&size=24\":{\"items\":[");

        assertThat(page("/_pages/species/t-visible"))
                .contains("<title data-page>Visible shrimp (Testus visibilis) – PW Aquatics</title>")
                .contains("<h1>Visible shrimp</h1>", "<h2>Available now</h2>")
                .contains("\"/species/t-visible\":{", "\"/products?species=t-visible&size=8\":{");
    }

    @Test
    void eachPackSizeIsOfferedAsItCanBeBought() throws Exception {
        // Beta Red: 3 animals left (low stock), so the 10-pack cannot be bought.
        assertThat(page("/_pages/products/beta-red"))
                .contains("\"sku\":\"T-BETA-1\",\"price\":\"2.00\",\"priceCurrency\":\"EUR\","
                        + "\"availability\":\"https://schema.org/LimitedAvailability\"")
                .contains("\"sku\":\"T-BETA-10\",\"price\":\"18.00\",\"priceCurrency\":\"EUR\","
                        + "\"availability\":\"https://schema.org/OutOfStock\"");
        // Coming soon cannot be ordered yet.
        assertThat(page("/_pages/products/gamma-snail"))
                .contains("\"sku\":\"T-GAMMA-1\",\"price\":\"5.00\",\"priceCurrency\":\"EUR\","
                        + "\"availability\":\"https://schema.org/OutOfStock\"");
    }

    @Test
    void theHomePageNamesTheShopAndCategoryPagesTheirWay() throws Exception {
        assertThat(page("/_pages/de"))
                .contains("{\"@context\":\"https://schema.org\",\"@type\":\"Organization\",\"name\":\"PW Aquatics\","
                        + "\"url\":\"" + SITE + "/\",\"logo\":\"" + SITE + "/brand/pw-aquatics-mark.svg\"}")
                .contains("{\"@context\":\"https://schema.org\",\"@type\":\"WebSite\",\"name\":\"PW Aquatics\","
                        + "\"url\":\"" + SITE + "/de/\",\"inLanguage\":\"de\"}");
        assertThat(page("/_pages/de/shop/t-neo"))
                .contains("\"itemListElement\":["
                        + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Shop\",\"item\":\"" + SITE + "/de/shop\"},"
                        + "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"Shrimp\",\"item\":\"" + SITE + "/de/shop/t-shrimp\"},"
                        + "{\"@type\":\"ListItem\",\"position\":3,\"name\":\"Neocaridina\",\"item\":\"" + SITE + "/de/shop/t-neo\"}]");
        // The whole shop has no way to it, and the other pages carry no structured data.
        assertThat(page("/_pages/shop")).doesNotContain("application/ld+json");
        assertThat(page("/_pages/about")).doesNotContain("application/ld+json");
    }

    @Test
    void laterPagesOfTheShopAreTheirOwnPages() throws Exception {
        // 24 more snails: the snail list has two pages.
        jdbc.update("""
                INSERT INTO products (id, slug, name, category_id, short_description, status, stock_units,
                                      low_stock_threshold, min_price_cents, published_at)
                SELECT ('00000000-0000-0000-0001-' || lpad(n::text, 12, '0'))::uuid, 't-snail-' || n, 'Snail ' || n,
                       '00000000-0000-0000-0000-0000000000c3', 'Snail', 'ACTIVE', 5, 2, 100, '2026-01-01T00:00:00Z'
                FROM generate_series(1, 24) AS n
                """);
        try {
            assertThat(page("/_pages/shop/t-snails"))
                    .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/shop/t-snails\">")
                    .contains("<a rel=\"next\" href=\"/shop/t-snails?page=2\">Next</a>")
                    .doesNotContain("rel=\"prev\"");

            assertThat(page("/_pages/de/shop/t-snails?page=2"))
                    .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/de/shop/t-snails?page=2\">")
                    .contains("<link data-page rel=\"alternate\" hreflang=\"en\" href=\"" + SITE + "/shop/t-snails?page=2\">")
                    .contains("<a rel=\"prev\" href=\"/de/shop/t-snails\">")
                    .doesNotContain("rel=\"next\"")
                    // The app's query for page 2 (it counts from 0).
                    .contains("\"/products?category=t-snails&page=1&size=24\":{\"items\":[");

            // A filtered, searched or sorted view points to the plain list.
            assertThat(page("/_pages/shop/t-snails?colour=t-red&page=2"))
                    .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/shop/t-snails\">")
                    .contains("\"/products?category=t-snails&size=24\":{");

            // Past the last page.
            mvc.perform(get("/_pages/shop/t-snails?page=3")).andExpect(status().isNotFound());
        } finally {
            jdbc.update("DELETE FROM products WHERE slug LIKE 't-snail-%'");
        }
    }

    @Test
    void theSitemapListsEveryPublicPageInItsLanguages() throws Exception {
        jdbc.update("UPDATE products SET description_de = 'Tiefes Blau.' WHERE slug = 'alpha-blue'");
        try {
            String xml = mvc.perform(get("/_site/sitemap.xml"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/xml;charset=UTF-8"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(xml)
                    .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
                    .contains("<loc>" + SITE + "/</loc>", "<loc>" + SITE + "/de/</loc>", "<loc>" + SITE + "/de/gallery</loc>")
                    .contains("<loc>" + SITE + "/shop/t-neo</loc>", "<loc>" + SITE + "/de/shop/t-neo</loc>")
                    .contains("<loc>" + SITE + "/species/t-visible</loc>")
                    // Translated: both languages, each naming the other.
                    .containsPattern("<url>\\n    <loc>" + SITE + "/de/products/alpha-blue</loc>\\n    <lastmod>[0-9T:-]+Z</lastmod>\\n"
                            + "    <xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"" + SITE + "/products/alpha-blue\"/>\\n"
                            + "    <xhtml:link rel=\"alternate\" hreflang=\"de\" href=\"" + SITE + "/de/products/alpha-blue\"/>\\n"
                            + "    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"" + SITE + "/products/alpha-blue\"/>\\n"
                            + "  </url>")
                    // Not translated: English only.
                    .contains("<loc>" + SITE + "/products/beta-red</loc>")
                    .doesNotContain("/de/products/beta-red</loc>", "/de/species/t-visible</loc>")
                    // Drafts, protected species and pages that are not indexed are left out.
                    .doesNotContain("delta-draft", "epsilon-banned", "t-banned", "/cart", "/account", "/legal")
                    .endsWith("</urlset>\n");
        } finally {
            jdbc.update("UPDATE products SET description_de = NULL WHERE slug = 'alpha-blue'");
        }
    }

    @Test
    void robotsTxtIsTheBuildsWithTheSitemapsLink() throws Exception {
        String robots = mvc.perform(get("/_site/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(robots).isEqualTo(FakeShellSource.ROBOTS + "\nSitemap: " + SITE + "/sitemap.xml\n");

        shells.setUnavailable(true);
        try {
            // The frontend's Caddy then serves the build's own robots.txt.
            mvc.perform(get("/_site/robots.txt")).andExpect(status().isServiceUnavailable());
        } finally {
            shells.setUnavailable(false);
        }
    }

    @Test
    void contentPagesUseTheTextsOfTheFrontendBuild() throws Exception {
        assertThat(page("/_pages/de/about/"))
                .contains("<title data-page>Über uns – PW Aquatics</title>", "<h1>Warum wir Garnelen verkaufen</h1>")
                .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/de/about\">");
        // A text the German file lacks falls back to English.
        assertThat(page("/_pages/de/contact")).contains("<title data-page>Contact – PW Aquatics</title>");

        assertThat(page("/_pages/"))
                .contains("<title data-page>PW Aquatics – Dwarf shrimp</title>")
                .contains("<link data-page rel=\"canonical\" href=\"" + SITE + "/\">")
                .contains("<link data-page rel=\"alternate\" hreflang=\"de\" href=\"" + SITE + "/de/\">")
                .contains("<a href=\"/products/alpha-blue\">Alpha Blue</a>")
                .contains("\"/products?featured=true&availability=IN_STOCK&size=4\":{");
    }

    @Test
    void addressesTheAppHasNoPageForAnswerNotFound() throws Exception {
        for (String path : new String[] {"/_pages/foo", "/_pages/de/bar/baz", "/_pages/products", "/_pages/de/de/shop"}) {
            String html = mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(path)
                    .contains("<meta data-page name=\"robots\" content=\"noindex\">", "<h1>This page has drifted away</h1>");
        }
        // The app's own pages, in any letter case, and everything under /admin (it redirects).
        for (String path : new String[] {"/_pages/Cart", "/_pages/de/account/orders/PW-2026-000001", "/_pages/admin/whatever"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void otherPagesKeepTheTemplateWithTheirLanguage() throws Exception {
        String account = page("/_pages/de/account/orders");
        assertThat(account)
                .contains("<html lang=\"de\">", "<title>PW Aquatics</title>", "Default description.", "<div id=\"root\"></div>")
                .doesNotContain("data-page");
    }

    @Test
    void textFromTheCatalogIsEscaped() throws Exception {
        jdbc.update("UPDATE products SET name = 'Alpha <b>Blue</b> \"x\"' WHERE slug = 'alpha-blue'");
        try {
            assertThat(page("/_pages/products/alpha-blue"))
                    .contains("<h1>Alpha &lt;b&gt;Blue&lt;/b&gt; &quot;x&quot;</h1>")
                    .contains("<title data-page>Alpha &lt;b&gt;Blue&lt;/b&gt; &quot;x&quot; – PW Aquatics</title>")
                    // Inside the JSON data blocks "<" is written as <.
                    .contains("Alpha \\u003cb>Blue\\u003c/b>")
                    .doesNotContain("<b>Blue");
        } finally {
            jdbc.update("UPDATE products SET name = 'Alpha Blue' WHERE slug = 'alpha-blue'");
        }
    }

    @Test
    void withoutATemplateTheProxyServesThePlainApp() throws Exception {
        shells.setUnavailable(true);
        try {
            mvc.perform(get("/_pages/products/alpha-blue")).andExpect(status().isServiceUnavailable());
        } finally {
            shells.setUnavailable(false);
        }
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
