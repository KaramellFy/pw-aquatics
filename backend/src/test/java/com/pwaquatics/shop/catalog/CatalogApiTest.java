package com.pwaquatics.shop.catalog;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@Sql(scripts = "/catalog/catalog-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/catalog/catalog-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class CatalogApiTest {

    @Autowired MockMvc mvc;

    @Test
    void listsOnlyVisibleProductsWithPurchasableOnesFirst() throws Exception {
        products("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[*].slug").value(contains("alpha-blue", "beta-red", "gamma-snail", "zeta-sold")))
                .andExpect(jsonPath("$.items[0].availability").value("IN_STOCK"))
                .andExpect(jsonPath("$.items[0].priceFrom.amount").value(300))
                .andExpect(jsonPath("$.items[0].priceFrom.currency").value("EUR"))
                .andExpect(jsonPath("$.items[0].compareAtPriceFrom").doesNotExist())
                .andExpect(jsonPath("$.items[0].hasMultipleVariants").value(true))
                .andExpect(jsonPath("$.items[0].grade.slug").value("t-high"))
                .andExpect(jsonPath("$.items[1].availability").value("LOW_STOCK"))
                .andExpect(jsonPath("$.items[2].availability").value("COMING_SOON"))
                .andExpect(jsonPath("$.items[3].availability").value("OUT_OF_STOCK"));
    }

    @Test
    void combinesFilters() throws Exception {
        slugs("colour=t-red&availability=IN_STOCK").andExpect(jsonPath("$.items[*].slug").value(contains("beta-red")));
        slugs("category=t-shrimp&sort=NAME_ASC")
                .andExpect(jsonPath("$.items[*].slug").value(contains("alpha-blue", "beta-red", "zeta-sold")));
        slugs("species=t-visible&grade=t-high").andExpect(jsonPath("$.items[*].slug").value(contains("alpha-blue")));
        slugs("minPrice=250&maxPrice=600&sort=PRICE_ASC")
                .andExpect(jsonPath("$.items[*].slug").value(contains("alpha-blue", "gamma-snail")));
        slugs("availability=COMING_SOON").andExpect(jsonPath("$.items[*].slug").value(contains("gamma-snail")));
        slugs("q=GAMMA").andExpect(jsonPath("$.items[*].slug").value(contains("gamma-snail")));
        slugs("q=visible%25").andExpect(jsonPath("$.totalItems").value(0)); // LIKE wildcards are escaped
        slugs("category=does-not-exist").andExpect(jsonPath("$.totalItems").value(0));
        slugs("colour=t-red,t-blue&sort=PRICE_DESC")
                .andExpect(jsonPath("$.items[*].slug").value(contains("zeta-sold", "gamma-snail", "alpha-blue", "beta-red")));
    }

    @Test
    void paginates() throws Exception {
        products("size=3&page=1&sort=NAME_ASC")
                .andExpect(jsonPath("$.items[*].slug").value(contains("zeta-sold")))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void facetsIgnoreTheirOwnFilter() throws Exception {
        mvc.perform(get("/api/v1/products/facets?colour=t-blue"))
                .andExpect(status().isOk())
                // colours are counted without the colour filter
                .andExpect(jsonPath("$.colours[?(@.slug == 't-red')].count").value(3))
                .andExpect(jsonPath("$.colours[?(@.slug == 't-blue')].count").value(1))
                .andExpect(jsonPath("$.colours[?(@.slug == 't-blue')].hexCode").value("#1565C0"))
                // other dimensions respect the colour filter
                .andExpect(jsonPath("$.species[?(@.slug == 't-visible')].count").value(1))
                .andExpect(jsonPath("$.grades[*].slug").value(contains("t-high")))
                .andExpect(jsonPath("$.availability.inStock").value(1))
                .andExpect(jsonPath("$.availability.comingSoon").value(0))
                .andExpect(jsonPath("$.price.min.amount").value(300))
                .andExpect(jsonPath("$.price.max.amount").value(300));
    }

    @Test
    void productDetailUsesServerSideAvailabilityAndSafeHtml() throws Exception {
        mvc.perform(get("/api/v1/products/beta-red"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("LOW_STOCK"))
                .andExpect(jsonPath("$.variants[0].available").value(true))
                .andExpect(jsonPath("$.variants[0].maxQuantity").value(3))
                .andExpect(jsonPath("$.variants[1].available").value(false))
                .andExpect(jsonPath("$.variants[1].maxQuantity").value(0))
                .andExpect(jsonPath("$.categoryPath[*].slug").value(contains("t-shrimp", "t-neo")))
                .andExpect(jsonPath("$.species.care.tempMinC").value(18.0))
                .andExpect(jsonPath("$.seo.title").value("Beta Red"));

        mvc.perform(get("/api/v1/products/alpha-blue"))
                .andExpect(jsonPath("$.descriptionHtml").value(containsString("<strong>blue</strong>")))
                .andExpect(jsonPath("$.descriptionHtml").value(containsString("&lt;script&gt;")))
                .andExpect(jsonPath("$.descriptionHtml").value(not(containsString("<script>"))))
                .andExpect(jsonPath("$.descriptionHtml").value(not(containsString("javascript:"))))
                .andExpect(jsonPath("$.variants[1].compareAtPrice.amount").value(3000));
    }

    @Test
    void hiddenProductsAreNotFound() throws Exception {
        for (String slug : new String[] {"delta-draft", "epsilon-banned", "nope"}) {
            mvc.perform(get("/api/v1/products/" + slug))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void relatedProductsPreferSameSpeciesAndAvailableOnes() throws Exception {
        mvc.perform(get("/api/v1/products/alpha-blue/related"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug").value(contains("beta-red", "zeta-sold")));
    }

    @Test
    void taxonomy() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[?(@.slug == 't-shrimp')].children[*].slug").value(contains("t-neo")));
        mvc.perform(get("/api/v1/species"))
                .andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.hasItem("t-visible")))
                .andExpect(jsonPath("$[*].slug").value(not(org.hamcrest.Matchers.hasItem("t-banned"))));
        mvc.perform(get("/api/v1/species/t-banned")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/species/t-visible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.care.difficulty").value("BEGINNER"));
        mvc.perform(get("/api/v1/colours")).andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.hasItem("t-red")));
        mvc.perform(get("/api/v1/grades")).andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.hasItem("t-high")));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        products("size=101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        products("sort=CHEAPEST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
        products("minPrice=-1").andExpect(status().isBadRequest());
    }

    private ResultActions products(String query) throws Exception {
        return mvc.perform(get("/api/v1/products?" + query));
    }

    private ResultActions slugs(String query) throws Exception {
        return products(query).andExpect(status().isOk());
    }
}
