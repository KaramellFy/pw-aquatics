package com.pwaquatics.shop.catalog;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** German texts the owner writes: shown on German requests, English otherwise and where missing (ADR-012). */
@IntegrationTest
@Sql(scripts = "/catalog/catalog-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/catalog/catalog-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class GermanTextsApiTest {

    private static final String ALPHA = "00000000-0000-0000-0000-000000000101";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aProductIsShownInGermanWhereTranslatedAndInEnglishOtherwise() throws Exception {
        jdbc.update("""
                UPDATE products SET name_de = 'Alpha Blau', description_de = 'Eine **blaue** Garnele.',
                       seo_title_de = NULL, short_description_de = NULL WHERE slug = 'alpha-blue'""");
        jdbc.update("UPDATE colours SET name_de = 'Blau' WHERE slug = 't-blue'");
        jdbc.update("UPDATE species SET common_name_de = 'Sichtbare Garnele' WHERE slug = 't-visible'");
        jdbc.update("UPDATE categories SET name_de = 'Garnelen' WHERE slug = 't-shrimp'");

        german(get("/api/v1/products/alpha-blue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Blau"))
                .andExpect(jsonPath("$.descriptionHtml").value("<p>Eine <strong>blaue</strong> Garnele.</p>"))
                .andExpect(jsonPath("$.seo.title").value("Alpha Blau"))
                .andExpect(jsonPath("$.species.commonName").value("Sichtbare Garnele"))
                .andExpect(jsonPath("$.categoryPath[0].name").value("Garnelen"))
                .andExpect(jsonPath("$.colours[*].name").value(hasItem("Blau")))
                .andExpect(jsonPath("$.languages").value(contains("en", "de")));

        mvc.perform(get("/api/v1/products/alpha-blue"))
                .andExpect(jsonPath("$.name").value("Alpha Blue"))
                .andExpect(jsonPath("$.species.commonName").value("Visible shrimp"));

        // Not translated: German requests get the English text, and the page says so.
        german(get("/api/v1/products/beta-red"))
                .andExpect(jsonPath("$.name").value("Beta Red"))
                .andExpect(jsonPath("$.languages").value(contains("en")));
    }

    @Test
    void customersFindProductsByTheirGermanName() throws Exception {
        jdbc.update("UPDATE products SET name_de = 'Gamma Schnecke' WHERE slug = 'gamma-snail'");
        mvc.perform(get("/api/v1/products?q=schnecke"))
                .andExpect(jsonPath("$.items[*].slug").value(contains("gamma-snail")));
        german(get("/api/v1/products?q=schnecke"))
                .andExpect(jsonPath("$.items[0].name").value("Gamma Schnecke"));
        jdbc.update("UPDATE colours SET name_de = 'Rot' WHERE slug = 't-red'");
        german(get("/api/v1/products/facets"))
                .andExpect(jsonPath("$.colours[*].name").value(hasItem("Rot")));
    }

    @Test
    void theOwnerWritesGermanTextsInTheAdminArea() throws Exception {
        Cookie admin = auth.adminSession(uniqueEmail());
        mvc.perform(json(post("/api/v1/admin/colours"), """
                        {"name":"Mint","nameDe":"  Minzgrün ","hexCode":"#3EB489","sortOrder":9}
                        """).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mint"))
                .andExpect(jsonPath("$.nameDe").value("Minzgrün"));
        german(get("/api/v1/colours")).andExpect(jsonPath("$[*].name").value(hasItem("Minzgrün")));
        mvc.perform(get("/api/v1/colours")).andExpect(jsonPath("$[*].name").value(hasItem("Mint")));

        jdbc.update("UPDATE products SET short_description_de = 'Kurz' WHERE slug = 'alpha-blue'");
        mvc.perform(get("/api/v1/admin/products/" + ALPHA).cookie(admin))
                .andExpect(jsonPath("$.name").value("Alpha Blue"))
                .andExpect(jsonPath("$.shortDescriptionDe").value("Kurz"));
        jdbc.update("DELETE FROM colours WHERE name = 'Mint'");
    }

    private ResultActions german(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mvc.perform(request.header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8"));
    }
}
