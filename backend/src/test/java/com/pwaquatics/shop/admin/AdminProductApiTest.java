package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.admin.AdminTestSupport.createCategory;
import static com.pwaquatics.shop.admin.AdminTestSupport.createProduct;
import static com.pwaquatics.shop.admin.AdminTestSupport.createSpecies;
import static com.pwaquatics.shop.admin.AdminTestSupport.photo;
import static com.pwaquatics.shop.admin.AdminTestSupport.productBody;
import static com.pwaquatics.shop.admin.AdminTestSupport.read;
import static com.pwaquatics.shop.admin.AdminTestSupport.unique;
import static com.pwaquatics.shop.admin.AdminTestSupport.variantBody;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.catalog.application.admin.AdminProductService;
import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** The product admin end to end: access rules, the editor's life cycle, photos and conflicts. */
@IntegrationTest
class AdminProductApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminProductService adminProducts;
    @Autowired AppProperties properties;

    private Cookie admin;
    private String categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = auth.adminSession(uniqueEmail());
        categoryId = createCategory(mvc, admin, "Admin test " + unique());
    }

    /** The database is shared with the public catalog tests, which count visible products. */
    @AfterEach
    void takePublishedProductsOutOfTheShop() {
        jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE category_id = ?::uuid", UUID.fromString(categoryId));
    }

    @Test
    void onlyAdministratorsGetIn() throws Exception {
        String productId = createProduct(mvc, admin, "Access " + unique(), categoryId, null);
        Cookie customer = auth.registeredSession(uniqueEmail());
        List<String> reads = List.of("/api/v1/admin/products", "/api/v1/admin/products/" + productId,
                "/api/v1/admin/categories", "/api/v1/admin/species", "/api/v1/admin/colours", "/api/v1/admin/grades",
                "/api/v1/admin/inventory/" + productId + "/movements");

        for (String path : reads) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(customer))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(get(path).cookie(admin)).andExpect(status().isOk());
        }
        mvc.perform(json(post("/api/v1/admin/products"), productBody("Nope", categoryId, null, "")).cookie(customer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/products/" + productId + "/publish").cookie(customer).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
                        .file(new MockMultipartFile("file", "a.jpg", "image/jpeg", photo(800, 600)))
                        .param("altText", "x").cookie(customer).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/admin/inventory/" + productId + "/adjustments"),
                        "{\"delta\":5,\"reason\":\"RESTOCK\"}").cookie(customer))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/admin/categories"), "{\"name\":\"X\",\"sortOrder\":0,\"active\":true}")
                        .cookie(customer))
                .andExpect(status().isForbidden());
        // Writes need the CSRF token even for administrators.
        mvc.perform(post("/api/v1/admin/products/" + productId + "/archive").cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        // The services check the role themselves too (method security).
        assertThatThrownBy(() -> adminProducts.get(UUID.fromString(productId)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void aProductIsCreatedPhotographedPublishedAndStockedThroughTheAdmin() throws Exception {
        String suffix = unique();
        String speciesId = createSpecies(mvc, admin, "Caridina journey " + suffix, false);
        String name = "Journey Blue " + suffix;

        MvcResult created = mvc.perform(json(post("/api/v1/admin/products"),
                        productBody(name, categoryId, speciesId, "")).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.slug").value("journey-blue-" + suffix))
                .andExpect(jsonPath("$.stockUnits").value(0))
                .andExpect(jsonPath("$.publishProblems").value(contains("NO_ACTIVE_VARIANT", "NO_IMAGE")))
                .andReturn();
        String id = read(created, "$.id");
        String slug = read(created, "$.slug");

        // Not in the shop while it is a draft.
        mvc.perform(get("/api/v1/products/" + slug)).andExpect(status().isNotFound());
        publish(id).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NO_ACTIVE_VARIANT"));

        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                        variantBody("jb-" + suffix + "-1", "1 animal", 1, 390, null, true)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants[0].sku").value(("JB-" + suffix + "-1").toUpperCase()))
                .andExpect(jsonPath("$.variants[0].price.amount").value(390));
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                        variantBody("JB-" + suffix + "-10", "10 animals", 10, 3490, 3900L, true)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants", hasSize(2)))
                .andExpect(jsonPath("$.publishProblems").value(contains("NO_IMAGE")));
        publish(id).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NO_IMAGE"));

        MvcResult uploaded = upload(id, photo(1700, 1300), "  Blue shrimp on moss  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.images[0].altText").value("Blue shrimp on moss"))
                .andExpect(jsonPath("$.images[0].width").value(1700))
                .andExpect(jsonPath("$.images[0].url", containsString(".webp")))
                .andExpect(jsonPath("$.publishProblems", empty()))
                .andReturn();
        String url = read(uploaded, "$.images[0].url");
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/webp"));
        String key = url.replace(properties.media().publicPath() + "/", "").replace("/800.webp", "");
        Path directory = Path.of(properties.media().localPath()).resolve(key);
        try (var files = Files.list(directory)) {
            assertThat(files.map(f -> f.getFileName().toString()))
                    .containsExactlyInAnyOrder("400.webp", "800.webp", "1200.webp", "1600.webp");
        }

        publish(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // Live, but sold out until stock arrives.
        mvc.perform(get("/api/v1/products/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.images[0].alt").value("Blue shrimp on moss"))
                .andExpect(jsonPath("$.descriptionHtml").value("<p><strong>Hardy</strong> and easy to keep.</p>"));
        mvc.perform(json(post("/api/v1/admin/inventory/" + id + "/adjustments"),
                        "{\"delta\":25,\"reason\":\"RESTOCK\",\"note\":\"New tank\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockUnits").value(25));
        mvc.perform(get("/api/v1/products/" + slug))
                .andExpect(jsonPath("$.availability").value("IN_STOCK"))
                .andExpect(jsonPath("$.variants[1].maxQuantity").value(2));
        mvc.perform(get("/api/v1/admin/inventory/" + id + "/movements").cookie(admin))
                .andExpect(jsonPath("$.items[0].reason").value("RESTOCK"))
                .andExpect(jsonPath("$.items[0].delta").value(25))
                .andExpect(jsonPath("$.items[0].stockAfter").value(25))
                .andExpect(jsonPath("$.items[0].note").value("New tank"))
                .andExpect(jsonPath("$.items[0].manual").value(true));

        // Every write is in the audit log, attributed to the administrator.
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?::uuid ORDER BY id",
                        String.class, id))
                .containsExactly("PRODUCT_CREATED", "VARIANT_CREATED", "VARIANT_CREATED", "IMAGE_UPLOADED",
                        "PRODUCT_PUBLISHED", "STOCK_ADJUSTED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE entity_id = ?::uuid AND actor_user_id IS NULL",
                        Integer.class, id))
                .isZero();

        // Archiving takes it out of the shop again.
        mvc.perform(post("/api/v1/admin/products/" + id + "/archive").cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/products/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    void editsAreValidatedAndConcurrentChangesDetected() throws Exception {
        String suffix = unique();
        String id = createProduct(mvc, admin, "Edit " + suffix, categoryId, null);
        String otherId = createProduct(mvc, admin, "Other " + suffix, categoryId, null);
        long version = ((Number) read(mvc.perform(get("/api/v1/admin/products/" + id).cookie(admin)).andReturn(),
                "$.version")).longValue();

        // Restocking while the editor is open does not get in the way of saving the texts.
        mvc.perform(json(post("/api/v1/admin/inventory/" + id + "/adjustments"),
                        "{\"delta\":3,\"reason\":\"RESTOCK\"}").cookie(admin))
                .andExpect(status().isOk());
        MvcResult saved = mvc.perform(json(put("/api/v1/admin/products/" + id),
                        productBody("Edited " + suffix, categoryId, null,
                                ",\"slug\":\"edited-" + suffix + "\",\"version\":" + version)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Edited " + suffix))
                .andExpect(jsonPath("$.slug").value("edited-" + suffix))
                .andExpect(jsonPath("$.stockUnits").value(3))
                .andReturn();
        assertThat(((Number) read(saved, "$.version")).longValue()).isGreaterThan(version);

        // Saving with the old version again: someone else saved in between.
        mvc.perform(json(put("/api/v1/admin/products/" + id),
                        productBody("Stale", categoryId, null, ",\"version\":" + version)).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(json(put("/api/v1/admin/products/" + id), productBody("No version", categoryId, null, ""))
                        .cookie(admin))
                .andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));

        // Web addresses and SKUs are unique; prices are checked.
        mvc.perform(json(put("/api/v1/admin/products/" + otherId), productBody("Other", categoryId, null,
                        ",\"slug\":\"edited-" + suffix + "\",\"version\":0")).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));
        mvc.perform(json(put("/api/v1/admin/products/" + otherId), productBody("Other", categoryId, null,
                        ",\"slug\":\"Not A Slug\",\"version\":0")).cookie(admin))
                .andExpect(jsonPath("$.code").value("SLUG_INVALID"));
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                        variantBody("SKU-" + suffix, "1 animal", 1, 500, null, true)).cookie(admin))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/admin/products/" + otherId + "/variants"),
                        variantBody("sku-" + suffix, "1 animal", 1, 500, null, true)).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_TAKEN"));
        mvc.perform(json(post("/api/v1/admin/products/" + otherId + "/variants"),
                        variantBody("CMP-" + suffix, "1 animal", 1, 500, 400L, true)).cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COMPARE_AT_PRICE_INVALID"));
        mvc.perform(json(post("/api/v1/admin/products/" + otherId + "/variants"),
                        "{\"sku\":\"bad sku!\",\"name\":\"\",\"packSize\":0,\"priceCents\":-1,\"active\":true}")
                        .cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(4)));
        mvc.perform(json(post("/api/v1/admin/products"),
                        productBody("Lost", UUID.randomUUID().toString(), null, "")).cookie(admin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/products/" + UUID.randomUUID()).cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void photosCanBeReorderedRelabelledAndDeleted() throws Exception {
        String suffix = unique();
        String id = createProduct(mvc, admin, "Photos " + suffix, categoryId, null);
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                variantBody("PH-" + suffix, "1 animal", 1, 500, null, true)).cookie(admin));

        upload(id, "not a photo".getBytes(), "x")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_UNSUPPORTED"));
        upload(id, photo(300, 300), "x").andExpect(jsonPath("$.code").value("IMAGE_TOO_SMALL"));

        String first = read(upload(id, photo(900, 900), "First").andReturn(), "$.images[0].id");
        MvcResult two = upload(id, photo(1200, 800), "Second").andExpect(status().isCreated()).andReturn();
        String second = read(two, "$.images[1].id");
        String secondUrl = read(two, "$.images[1].url");

        mvc.perform(json(patch("/api/v1/admin/products/" + id + "/images/order"),
                        "{\"imageIds\":[\"%s\",\"%s\"]}".formatted(second, first)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[*].altText").value(contains("Second", "First")));
        mvc.perform(json(patch("/api/v1/admin/products/" + id + "/images/order"),
                        "{\"imageIds\":[\"%s\"]}".formatted(second)).cookie(admin))
                .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        mvc.perform(json(patch("/api/v1/admin/products/" + id + "/images/" + first),
                        "{\"altText\":\"Side view\"}").cookie(admin))
                .andExpect(jsonPath("$.images[1].altText").value("Side view"));

        publish(id).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/products/" + id + "/images/" + second).cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[*].altText").value(contains("Side view")));
        // The files are gone once the change is saved.
        mvc.perform(get(secondUrl)).andExpect(status().isNotFound());
        // A published product keeps at least one photo.
        mvc.perform(delete("/api/v1/admin/products/" + id + "/images/" + first).cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_IMAGE"));
        mvc.perform(delete("/api/v1/admin/products/" + id + "/images/" + UUID.randomUUID()).cookie(admin).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCopyStartsAsADraftWithoutStockOrPhotos() throws Exception {
        String suffix = unique();
        String id = createProduct(mvc, admin, "Original " + suffix, categoryId, null);
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                variantBody("OR-" + suffix + "-1", "1 animal", 1, 500, null, true)).cookie(admin));
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                variantBody("OR-" + suffix + "-5", "5 animals", 5, 2200, null, false)).cookie(admin));
        upload(id, photo(800, 800), "Original photo");
        mvc.perform(json(post("/api/v1/admin/inventory/" + id + "/adjustments"),
                "{\"delta\":10,\"reason\":\"RESTOCK\"}").cookie(admin));

        for (int copy = 1; copy <= 2; copy++) {
            String expectedSlug = "original-" + suffix + "-copy" + (copy == 1 ? "" : "-2");
            String expectedSku = "OR-" + suffix.toUpperCase() + "-1-COPY" + (copy == 1 ? "" : "-2");
            mvc.perform(post("/api/v1/admin/products/" + id + "/duplicate").cookie(admin).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.name").value("Original " + suffix + " (copy)"))
                    .andExpect(jsonPath("$.slug").value(expectedSlug))
                    .andExpect(jsonPath("$.stockUnits").value(0))
                    .andExpect(jsonPath("$.images", empty()))
                    .andExpect(jsonPath("$.variants[0].sku").value(expectedSku))
                    .andExpect(jsonPath("$.variants[1].active").value(false));
        }
    }

    @Test
    void productsOfRestrictedSpeciesNeverReachTheShop() throws Exception {
        String suffix = unique();
        String restricted = createSpecies(mvc, admin, "Procambarus test " + suffix, true);
        String id = createProduct(mvc, admin, "Marbled " + suffix, categoryId, restricted);
        mvc.perform(json(post("/api/v1/admin/products/" + id + "/variants"),
                variantBody("MB-" + suffix, "1 animal", 1, 900, null, true)).cookie(admin));
        upload(id, photo(800, 800), "Crayfish");

        publish(id).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SPECIES_RESTRICTED"));

        // A species that becomes restricted takes its live products with it.
        String allowed = createSpecies(mvc, admin, "Neocaridina later " + suffix, false);
        String liveId = createProduct(mvc, admin, "Later " + suffix, categoryId, allowed);
        mvc.perform(json(post("/api/v1/admin/products/" + liveId + "/variants"),
                variantBody("LT-" + suffix, "1 animal", 1, 900, null, true)).cookie(admin));
        upload(liveId, photo(800, 800), "Shrimp");
        String slug = read(publish(liveId).andExpect(status().isOk()).andReturn(), "$.slug");
        mvc.perform(get("/api/v1/products/" + slug)).andExpect(status().isOk());

        mvc.perform(json(put("/api/v1/admin/species/" + allowed), """
                        {"scientificName":"Neocaridina later %s","tradeRestricted":true,"restrictionNote":"Listed"}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/products/" + liveId).cookie(admin))
                .andExpect(jsonPath("$.publishProblems").value(contains("SPECIES_RESTRICTED")));
    }

    @Test
    void theProductListFiltersBySearchStatusAndStock() throws Exception {
        String suffix = unique();
        String low = createProduct(mvc, admin, "Filter Low " + suffix, categoryId, null);
        String out = createProduct(mvc, admin, "Filter Out " + suffix, categoryId, null);
        String plenty = createProduct(mvc, admin, "Filter Plenty " + suffix, categoryId, null);
        mvc.perform(json(post("/api/v1/admin/inventory/" + low + "/adjustments"),
                "{\"delta\":2,\"reason\":\"RESTOCK\"}").cookie(admin));
        mvc.perform(json(post("/api/v1/admin/inventory/" + plenty + "/adjustments"),
                "{\"delta\":50,\"reason\":\"RESTOCK\"}").cookie(admin));
        mvc.perform(json(post("/api/v1/admin/products/" + plenty + "/variants"),
                variantBody("FP-" + suffix, "1 animal", 1, 700, null, true)).cookie(admin));

        list("q=" + suffix + "&categoryId=" + categoryId + "&sort=NAME_ASC")
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[*].name").value(contains(
                        "Filter Low " + suffix, "Filter Out " + suffix, "Filter Plenty " + suffix)))
                .andExpect(jsonPath("$.items[2].stockUnits").value(50))
                .andExpect(jsonPath("$.items[2].priceFrom.amount").value(700))
                .andExpect(jsonPath("$.items[2].variantCount").value(1));
        list("q=" + suffix + "&stock=LOW_STOCK").andExpect(jsonPath("$.items[*].id").value(contains(low)));
        list("q=" + suffix + "&stock=OUT_OF_STOCK").andExpect(jsonPath("$.items[*].id").value(contains(out)));
        list("q=fp-" + suffix).andExpect(jsonPath("$.items[*].id").value(contains(plenty))); // by SKU
        list("q=" + suffix + "&status=ACTIVE").andExpect(jsonPath("$.totalItems").value(0));
        list("q=" + suffix + "&size=2&sort=STOCK_ASC")
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[*].id").value(contains(out, low)));
        list("stock=SOMETIMES").andExpect(status().isBadRequest());
    }

    @Test
    void markdownIsPreviewedExactlyAsTheShopRendersIt() throws Exception {
        mvc.perform(json(post("/api/v1/admin/markdown/preview"),
                        "{\"markdown\":\"**Bold** <script>alert(1)</script> [x](javascript:alert(1))\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.html", containsString("<strong>Bold</strong>")))
                .andExpect(jsonPath("$.html", containsString("&lt;script&gt;")))
                .andExpect(jsonPath("$.html", org.hamcrest.Matchers.not(containsString("javascript:"))));
    }

    private ResultActions publish(String id) throws Exception {
        return mvc.perform(post("/api/v1/admin/products/" + id + "/publish").cookie(admin).with(csrf()));
    }

    private ResultActions upload(String id, byte[] file, String altText) throws Exception {
        return mvc.perform(multipart("/api/v1/admin/products/" + id + "/images")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", file))
                .param("altText", altText)
                .cookie(admin)
                .with(csrf())
                .with(uniqueClientIp()));
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get("/api/v1/admin/products?" + query).cookie(admin));
    }
}
