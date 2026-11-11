package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.admin.AdminTestSupport.createCategory;
import static com.pwaquatics.shop.admin.AdminTestSupport.createProduct;
import static com.pwaquatics.shop.admin.AdminTestSupport.createSpecies;
import static com.pwaquatics.shop.admin.AdminTestSupport.read;
import static com.pwaquatics.shop.admin.AdminTestSupport.unique;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class AdminTaxonomyAndInventoryApiTest {

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = auth.adminSession(uniqueEmail());
    }

    // ------------------------------------------------------------------ taxonomy

    @Test
    void categoriesFormATreeAndOnlyEmptyOnesCanBeDeleted() throws Exception {
        String suffix = unique();
        String shrimp = createCategory(mvc, admin, "Garnelen Größe " + suffix);
        mvc.perform(get("/api/v1/admin/categories").cookie(admin))
                .andExpect(jsonPath("$[?(@.id == '%s')].slug".formatted(shrimp))
                        .value(contains("garnelen-groesse-" + suffix)));

        String child = read(mvc.perform(json(post("/api/v1/admin/categories"), """
                        {"name":"Caridina %s","parentId":"%s","sortOrder":1,"active":true}
                        """.formatted(suffix, shrimp)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(shrimp))
                .andReturn(), "$.id");

        // A category cannot move inside its own branch.
        mvc.perform(json(put("/api/v1/admin/categories/" + shrimp), """
                        {"name":"Garnelen %s","parentId":"%s","sortOrder":0,"active":true}
                        """.formatted(suffix, child)).cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_CYCLE"));
        mvc.perform(json(post("/api/v1/admin/categories"), """
                        {"name":"Copy","slug":"caridina-%s","sortOrder":1,"active":true}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

        // In use: sub-category, then a product.
        deleteThing("categories", shrimp).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IN_USE"));
        createProduct(mvc, admin, "In child " + suffix, child, null);
        deleteThing("categories", child).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/categories").cookie(admin))
                .andExpect(jsonPath("$[?(@.id == '%s')].productCount".formatted(child)).value(contains(1)));

        String empty = createCategory(mvc, admin, "Empty " + suffix);
        deleteThing("categories", empty).andExpect(status().isNoContent());
        // Inactive categories disappear from the shop's navigation.
        mvc.perform(json(put("/api/v1/admin/categories/" + shrimp), """
                        {"name":"Hidden %s","sortOrder":0,"active":false}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$..slug", not(hasItem("hidden-" + suffix))));
    }

    @Test
    void speciesKeepTheirCareDataConsistent() throws Exception {
        String suffix = unique();
        String id = createSpecies(mvc, admin, "Neocaridina admin " + suffix, false);
        mvc.perform(get("/api/v1/admin/species/" + id).cookie(admin))
                .andExpect(jsonPath("$.slug").value("neocaridina-admin-" + suffix))
                .andExpect(jsonPath("$.tempMinC").value(18.0))
                .andExpect(jsonPath("$.phMax").value(7.8))
                .andExpect(jsonPath("$.difficulty").value("BEGINNER"));

        mvc.perform(json(put("/api/v1/admin/species/" + id), """
                        {"scientificName":"Neocaridina admin %s","phMin":8.0,"phMax":6.0,"tradeRestricted":false}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RANGE_INVALID"));
        mvc.perform(json(put("/api/v1/admin/species/" + id), """
                        {"scientificName":"Neocaridina admin %s","phMin":15,"tradeRestricted":false}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/admin/species"), """
                        {"scientificName":"NEOCARIDINA ADMIN %s","tradeRestricted":false}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCIENTIFIC_NAME_TAKEN"));

        String category = createCategory(mvc, admin, "Species cat " + suffix);
        createProduct(mvc, admin, "Uses species " + suffix, category, id);
        deleteThing("species", id).andExpect(status().isConflict());
        String unused = createSpecies(mvc, admin, "Unused " + suffix, false);
        deleteThing("species", unused).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/species/" + unused).cookie(admin)).andExpect(status().isNotFound());
    }

    @Test
    void coloursAndGradesAreManagedLikeTheRest() throws Exception {
        String suffix = unique();
        String colour = read(mvc.perform(json(post("/api/v1/admin/colours"), """
                        {"name":"Türkis %s","hexCode":"#40e0d0","sortOrder":3}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("tuerkis-" + suffix))
                .andExpect(jsonPath("$.hexCode").value("#40E0D0"))
                .andReturn(), "$.id");
        mvc.perform(json(post("/api/v1/admin/colours"), "{\"name\":\"Bad\",\"hexCode\":\"red\",\"sortOrder\":0}")
                        .cookie(admin))
                .andExpect(status().isBadRequest());

        String grade = read(mvc.perform(json(post("/api/v1/admin/grades"), """
                        {"name":"SSS %s","scale":"CARIDINA_CRS","rank":90,"sortOrder":1}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rank").value(90))
                .andReturn(), "$.id");
        mvc.perform(json(put("/api/v1/admin/grades/" + grade), """
                        {"name":"SSS+ %s","rank":95,"sortOrder":1}
                        """.formatted(suffix)).cookie(admin))
                .andExpect(jsonPath("$.name").value("SSS+ " + suffix))
                .andExpect(jsonPath("$.slug").value("sss-" + suffix));

        String category = createCategory(mvc, admin, "Colour cat " + suffix);
        mvc.perform(json(post("/api/v1/admin/products"), """
                        {"name":"Coloured %s","categoryId":"%s","gradeId":"%s","colourIds":["%s"],
                         "liveAnimal":true,"featured":false,"lowStockThreshold":5,"taxRateBps":1900}
                        """.formatted(suffix, category, grade, colour)).cookie(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.colourIds").value(contains(colour)))
                .andExpect(jsonPath("$.gradeId").value(grade));
        deleteThing("colours", colour).andExpect(status().isConflict());
        deleteThing("grades", grade).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/colours").cookie(admin))
                .andExpect(jsonPath("$[?(@.id == '%s')].productCount".formatted(colour)).value(contains(1)));
    }

    // ------------------------------------------------------------------ inventory

    @Test
    void stockCorrectionsFollowTheirReasonAndNeverGoBelowZero() throws Exception {
        String suffix = unique();
        String id = createProduct(mvc, admin, "Stock " + suffix, createCategory(mvc, admin, "Stock " + suffix), null);

        adjust(id, 20, "RESTOCK", "Delivery").andExpect(jsonPath("$.stockUnits").value(20));
        adjust(id, -3, "LOSS", "Did not survive acclimatisation").andExpect(jsonPath("$.stockUnits").value(17));
        adjust(id, 1, "RETURN", null).andExpect(jsonPath("$.stockUnits").value(18));
        adjust(id, -2, "MANUAL_ADJUSTMENT", "Miscounted").andExpect(jsonPath("$.stockUnits").value(16));

        for (String[] wrong : new String[][] {{"3", "LOSS"}, {"-3", "RESTOCK"}, {"-1", "RETURN"}, {"0", "MANUAL_ADJUSTMENT"},
                {"5", "ORDER_PLACED"}, {"5", "INITIAL"}}) {
            adjust(id, Integer.parseInt(wrong[0]), wrong[1], null)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("ADJUSTMENT_INVALID"));
        }
        adjust(id, -17, "LOSS", null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));
        adjust(UUID.randomUUID().toString(), 5, "RESTOCK", null).andExpect(status().isNotFound());

        // Stocktake: the counted number wins; the difference goes into the ledger.
        count(id, 12, "Monthly count").andExpect(jsonPath("$.stockUnits").value(12));
        count(id, 12, null).andExpect(jsonPath("$.stockUnits").value(12));
        count(id, -1, null).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/admin/inventory/" + id + "/movements?size=3").cookie(admin))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].reason").value("MANUAL_ADJUSTMENT"))
                .andExpect(jsonPath("$.items[0].delta").value(-4))
                .andExpect(jsonPath("$.items[0].stockAfter").value(12))
                .andExpect(jsonPath("$.items[0].note").value("Stocktake: Monthly count"))
                .andExpect(jsonPath("$.items[*].reason").value(contains("MANUAL_ADJUSTMENT", "MANUAL_ADJUSTMENT", "RETURN")));
        assertThat(jdbc.queryForObject("SELECT sum(delta) FROM stock_movements WHERE product_id = ?::uuid",
                        Integer.class, id))
                .isEqualTo(12);
        mvc.perform(get("/api/v1/admin/inventory/" + UUID.randomUUID() + "/movements").cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void simultaneousLossesCannotTakeMoreThanIsThere() throws Exception {
        String suffix = unique();
        String id = createProduct(mvc, admin, "Race " + suffix, createCategory(mvc, admin, "Race " + suffix), null);
        adjust(id, 5, "RESTOCK", null).andExpect(status().isOk());

        CyclicBarrier start = new CyclicBarrier(2);
        int[] statuses = new int[2];
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<?> a = pool.submit(() -> statuses[0] = race(start, id));
            Future<?> b = pool.submit(() -> statuses[1] = race(start, id));
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat(jdbc.queryForObject("SELECT stock_units FROM products WHERE id = ?::uuid", Integer.class, id))
                .isEqualTo(1);
    }

    private int race(CyclicBarrier start, String id) {
        try {
            start.await(10, TimeUnit.SECONDS);
            return adjust(id, -4, "LOSS", null).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultActions adjust(String id, int delta, String reason, String note) throws Exception {
        return mvc.perform(json(post("/api/v1/admin/inventory/" + id + "/adjustments"),
                "{\"delta\":%d,\"reason\":\"%s\",\"note\":%s}".formatted(delta, reason,
                        note == null ? "null" : "\"" + note + "\"")).cookie(admin));
    }

    private ResultActions count(String id, int counted, String note) throws Exception {
        return mvc.perform(json(put("/api/v1/admin/inventory/" + id + "/count"),
                "{\"countedUnits\":%d,\"note\":%s}".formatted(counted, note == null ? "null" : "\"" + note + "\""))
                .cookie(admin));
    }

    private ResultActions deleteThing(String type, String id) throws Exception {
        return mvc.perform(delete("/api/v1/admin/" + type + "/" + id).cookie(admin).with(csrf()));
    }
}
