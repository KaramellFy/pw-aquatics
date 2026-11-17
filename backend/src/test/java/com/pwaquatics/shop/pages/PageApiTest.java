package com.pwaquatics.shop.pages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .contains("\"@type\":\"Product\"", "\"name\":\"Alpha Blue (Testus visibilis)\"",
                        "\"lowPrice\":\"3.00\"", "\"highPrice\":\"25.00\"", "\"offerCount\":2",
                        "\"availability\":\"https://schema.org/InStock\"")
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
                .contains("\"/categories\":[", "\"/products?category=t-shrimp&size=24\":{\"items\":[");

        assertThat(page("/_pages/species/t-visible"))
                .contains("<title data-page>Visible shrimp (Testus visibilis) – PW Aquatics</title>")
                .contains("<h1>Visible shrimp</h1>", "<h2>Available now</h2>")
                .contains("\"/species/t-visible\":{", "\"/products?species=t-visible&size=8\":{");
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
