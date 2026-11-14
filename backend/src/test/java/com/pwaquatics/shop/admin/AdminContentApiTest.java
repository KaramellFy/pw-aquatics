package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.admin.AdminTestSupport.photo;
import static com.pwaquatics.shop.admin.AdminTestSupport.read;
import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.common.config.AppProperties;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** The gallery manager and the contact inbox (docs/API.md §2.3, §3.7). */
@IntegrationTest
class AdminContentApiTest {

    private static final String GALLERY = "/api/v1/admin/gallery";
    private static final String INBOX = "/api/v1/admin/contact-messages";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppProperties properties;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = auth.adminSession(uniqueEmail());
        jdbc.update("DELETE FROM gallery_images");
        jdbc.update("DELETE FROM contact_messages");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM gallery_images");
        jdbc.update("DELETE FROM contact_messages");
    }

    // ------------------------------------------------------------------ gallery

    @Test
    void onlyAdministratorsManageTheGalleryAndTheInbox() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());
        for (String path : new String[] {GALLERY, INBOX, INBOX + "/counts"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        }
        mvc.perform(multipart(GALLERY).file(new MockMultipartFile("file", "a.jpg", "image/jpeg", photo(800, 600)))
                        .param("altText", "x").cookie(customer).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch(INBOX + "/" + UUID.randomUUID()), "{\"status\":\"READ\"}").cookie(customer))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gallery_images", Integer.class)).isZero();
    }

    @Test
    void photosAreUploadedUnpublishedAndShownOnceTheOwnerPublishesThem() throws Exception {
        MvcResult uploaded = upload(photo(1700, 1100), "  Blue Dream shrimp grazing on moss  ", "Blue Dream", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.altText").value("Blue Dream shrimp grazing on moss"))
                .andExpect(jsonPath("$.title").value("Blue Dream"))
                .andExpect(jsonPath("$.caption").doesNotExist())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.sortOrder").value(0))
                .andExpect(jsonPath("$.image.width").value(1700))
                .andExpect(jsonPath("$.image.url").value(containsString("/gallery/")))
                .andReturn();
        String id = read(uploaded, "$.id");
        // Renditions are stored, never the upload itself.
        String url = read(uploaded, "$.image.url");
        Path directory = Path.of(properties.media().localPath())
                .resolve(url.replace(properties.media().publicPath() + "/", "").replace("/800.webp", ""));
        try (var files = Files.list(directory)) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .containsExactlyInAnyOrder("400.webp", "800.webp", "1200.webp", "1600.webp");
        }

        // Not on the public page yet.
        mvc.perform(get("/api/v1/gallery")).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(json(put(GALLERY + "/" + id), """
                        {"title":"  ","caption":"Bred in our own tanks.","altText":"Blue shrimp on moss",
                         "published":true,"version":0}
                        """).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
        // An editor working on the old version is told so.
        mvc.perform(json(put(GALLERY + "/" + id), """
                        {"altText":"Something else","published":false,"version":0}
                        """).cookie(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mvc.perform(get("/api/v1/gallery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].caption").value("Bred in our own tanks."))
                .andExpect(jsonPath("$.items[0].image.alt").value("Blue shrimp on moss"))
                .andExpect(jsonPath("$.items[0].image.srcSet").value(containsString("1600.webp 1600w")));

        mvc.perform(delete(GALLERY + "/" + id).cookie(admin).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNoContent());
        assertThat(Files.exists(directory)).isFalse();
        mvc.perform(get("/api/v1/gallery")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(delete(GALLERY + "/" + id).cookie(admin).with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_id = ?::uuid ORDER BY id",
                        String.class, id))
                .containsExactly("GALLERY_IMAGE_UPLOADED", "GALLERY_IMAGE_UPDATED", "GALLERY_IMAGE_DELETED");
    }

    @Test
    void unusablePhotosAndTextsAreRefused() throws Exception {
        upload(photo(300, 300), "Tiny", null, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_SMALL"));
        upload("not a photo".getBytes(), "Text", null, null)
                .andExpect(status().isUnprocessableContent());
        upload(photo(800, 600), "  ", null, null).andExpect(status().isBadRequest());
        upload(photo(800, 600), "x".repeat(256), null, null).andExpect(status().isBadRequest());
        upload(photo(800, 600), "Fine", null, "x".repeat(501)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM gallery_images", Integer.class)).isZero();
    }

    @Test
    void theOwnerDecidesTheOrder() throws Exception {
        String first = read(upload(photo(800, 600), "First", null, null).andReturn(), "$.id");
        String second = read(upload(photo(800, 600), "Second", null, null).andReturn(), "$.id");
        String third = read(upload(photo(800, 600), "Third", null, null).andReturn(), "$.id");
        mvc.perform(get(GALLERY).cookie(admin)).andExpect(jsonPath("$[*].id").value(contains(first, second, third)));

        reorder("[\"%s\",\"%s\",\"%s\"]".formatted(third, first, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(third, first, second)))
                .andExpect(jsonPath("$[*].sortOrder").value(contains(0, 1, 2)));
        jdbc.update("UPDATE gallery_images SET published = true");
        mvc.perform(get("/api/v1/gallery").param("size", "2"))
                .andExpect(jsonPath("$.items[*].id").value(contains(third, first)))
                .andExpect(jsonPath("$.totalItems").value(3));

        // Every photo exactly once.
        for (String ids : new String[] {
                "[\"%s\",\"%s\"]".formatted(third, first),
                "[\"%s\",\"%s\",\"%s\"]".formatted(third, first, first),
                "[\"%s\",\"%s\",\"%s\"]".formatted(third, first, UUID.randomUUID())}) {
            reorder(ids).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("IMAGE_ORDER_INVALID"));
        }
        mvc.perform(get("/api/v1/gallery").param("size", "49")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ inbox

    @Test
    void contactMessagesArriveAsNewAndAreSortedByTheOwner() throws Exception {
        String question = message("Question about Amano shrimp", "NEW", 2);
        String older = message("Order arrived", "NEW", 5);
        String spam = message("Cheap watches", "SPAM", 1);

        mvc.perform(get(INBOX).cookie(admin))
                .andExpect(status().isOk())
                // Without a status: everything but spam, newest first.
                .andExpect(jsonPath("$.items[*].id").value(contains(question, older)))
                .andExpect(jsonPath("$.items[0].subject").value("Question about Amano shrimp"))
                .andExpect(jsonPath("$.items[0].email").value("lena@example.com"))
                .andExpect(jsonPath("$.items[0].message").value("Hello, I have a question."))
                .andExpect(jsonPath("$.items[0].status").value("NEW"));
        mvc.perform(get(INBOX).param("status", "SPAM").cookie(admin))
                .andExpect(jsonPath("$.items[*].id").value(contains(spam)));
        mvc.perform(get(INBOX + "/counts").cookie(admin))
                .andExpect(jsonPath("$.NEW").value(2))
                .andExpect(jsonPath("$.SPAM").value(1))
                .andExpect(jsonPath("$.ANSWERED").value(0));

        changeStatus(question, "ANSWERED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"));
        changeStatus(older, "SPAM").andExpect(status().isOk());
        mvc.perform(get(INBOX + "/counts").cookie(admin))
                .andExpect(jsonPath("$.NEW").value(0))
                .andExpect(jsonPath("$.SPAM").value(2));

        changeStatus(question, "DELETED").andExpect(status().isBadRequest());
        changeStatus(UUID.randomUUID().toString(), "READ").andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject(
                        "SELECT details->>'to' FROM audit_log WHERE entity_id = ?::uuid AND action = 'CONTACT_MESSAGE_STATUS_CHANGED'",
                        String.class, question))
                .isEqualTo("ANSWERED");
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions upload(byte[] file, String altText, String title, String caption) throws Exception {
        var request = multipart(GALLERY)
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", file))
                .param("altText", altText);
        if (title != null) {
            request.param("title", title);
        }
        if (caption != null) {
            request.param("caption", caption);
        }
        return mvc.perform(request.cookie(admin).with(csrf()).with(uniqueClientIp()));
    }

    private ResultActions reorder(String ids) throws Exception {
        return mvc.perform(json(patch(GALLERY + "/order"), "{\"imageIds\":%s}".formatted(ids)).cookie(admin));
    }

    private ResultActions changeStatus(String id, String status) throws Exception {
        return mvc.perform(json(patch(INBOX + "/" + id), "{\"status\":\"%s\"}".formatted(status)).cookie(admin));
    }

    private String message(String subject, String status, int daysAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contact_messages (id, name, email, subject, message, status, created_at)
                VALUES (?, 'Lena', 'lena@example.com', ?, 'Hello, I have a question.', ?, now() - make_interval(days => ?))
                """, id, subject, status, daysAgo);
        return id.toString();
    }
}
