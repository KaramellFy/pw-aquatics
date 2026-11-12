package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Builders for the admin API tests: taxonomy, products and test photos. */
final class AdminTestSupport {

    private AdminTestSupport() {}

    static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    static String createCategory(MockMvc mvc, Cookie admin, String name) throws Exception {
        return read(mvc.perform(json(post("/api/v1/admin/categories"), """
                        {"name":"%s","sortOrder":5,"active":true}
                        """.formatted(name)).cookie(admin))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    static String createSpecies(MockMvc mvc, Cookie admin, String scientificName, boolean restricted)
            throws Exception {
        return read(mvc.perform(json(post("/api/v1/admin/species"), """
                        {"scientificName":"%s","commonName":"Test shrimp","tempMinC":18.0,"tempMaxC":26.5,
                         "phMin":6.5,"phMax":7.8,"difficulty":"BEGINNER","tradeRestricted":%s,
                         "restrictionNote":%s}
                        """.formatted(scientificName, restricted, restricted ? "\"EU invasive species\"" : "null"))
                        .cookie(admin))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    static String productBody(String name, String categoryId, String speciesId, String extra) {
        return """
                {"name":"%s","categoryId":"%s","speciesId":%s,"colourIds":[],
                 "shortDescription":"A calm, colourful shrimp.","description":"**Hardy** and easy to keep.",
                 "liveAnimal":true,"featured":false,"lowStockThreshold":5,"taxRateBps":1900%s}
                """.formatted(name, categoryId, speciesId == null ? "null" : "\"" + speciesId + "\"", extra);
    }

    static String createProduct(MockMvc mvc, Cookie admin, String name, String categoryId, String speciesId)
            throws Exception {
        return read(mvc.perform(json(post("/api/v1/admin/products"), productBody(name, categoryId, speciesId, ""))
                        .cookie(admin))
                .andExpect(status().isCreated()).andReturn(), "$.id");
    }

    static String variantBody(String sku, String name, int packSize, long priceCents, Long compareAt, boolean active) {
        return """
                {"sku":"%s","name":"%s","packSize":%d,"priceCents":%d,"compareAtPriceCents":%s,"active":%s}
                """.formatted(sku, name, packSize, priceCents, compareAt == null ? "null" : compareAt, active);
    }

    /** A plain JPEG photo of the given size. */
    static byte[] photo(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x1565C0));
        graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
