package com.pwaquatics.shop.customer;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
public class AddressApiTest {

    public static final String ADDRESS = """
            {"label":"Home","firstName":"Anna","lastName":"Schmidt","street":"Hauptstraße","houseNumber":"12a",
             "postalCode":"10115","city":"Berlin","countryCode":"de","phone":"+49 30 123456"}
            """;

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;

    @Test
    void theFirstAddressBecomesTheDefaultAndDefaultsCanBeMoved() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());

        String first = create(session, ADDRESS);
        String second = create(session, ADDRESS.replace("Home", "Office").replace("Berlin", "Hamburg"));

        mvc.perform(get("/api/v1/account/addresses").cookie(session))
                .andExpect(jsonPath("$[*].id").value(contains(first, second)))
                .andExpect(jsonPath("$[0].defaultShipping").value(true))
                .andExpect(jsonPath("$[0].defaultBilling").value(true))
                .andExpect(jsonPath("$[0].countryCode").value("DE"))
                .andExpect(jsonPath("$[1].defaultShipping").value(false));

        mvc.perform(json(put("/api/v1/account/addresses/" + second + "/default"), "{\"shipping\":true,\"billing\":false}")
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].defaultShipping".formatted(second)).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].defaultShipping".formatted(first)).value(false))
                .andExpect(jsonPath("$[?(@.id == '%s')].defaultBilling".formatted(first)).value(true));

        mvc.perform(json(put("/api/v1/account/addresses/" + second + "/default"), "{\"shipping\":false,\"billing\":false}")
                        .cookie(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingADefaultAddressPromotesAnother() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());
        String first = create(session, ADDRESS);
        String second = create(session, ADDRESS.replace("Home", "Office"));

        mvc.perform(delete("/api/v1/account/addresses/" + first).cookie(session).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/account/addresses").cookie(session))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[0].defaultShipping").value(true))
                .andExpect(jsonPath("$[0].defaultBilling").value(true));
    }

    @Test
    void addressesCanBeEditedAndAreValidated() throws Exception {
        Cookie session = auth.registeredSession(uniqueEmail());
        String id = create(session, ADDRESS);

        mvc.perform(json(put("/api/v1/account/addresses/" + id), ADDRESS.replace("Berlin", "Potsdam")).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Potsdam"))
                .andExpect(jsonPath("$.defaultShipping").value(true));

        mvc.perform(json(post("/api/v1/account/addresses"), ADDRESS.replace("\"Berlin\"", "\"\"")).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("city"));
        mvc.perform(json(post("/api/v1/account/addresses"), ADDRESS.replace("\"de\"", "\"zz\"")).cookie(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COUNTRY_INVALID"));
    }

    @Test
    void anotherCustomersAddressIsNotFound() throws Exception {
        Cookie owner = auth.registeredSession(uniqueEmail());
        Cookie stranger = auth.registeredSession(uniqueEmail());
        String id = create(owner, ADDRESS);

        mvc.perform(json(put("/api/v1/account/addresses/" + id), ADDRESS).cookie(stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(json(put("/api/v1/account/addresses/" + id + "/default"), "{\"shipping\":true,\"billing\":true}")
                        .cookie(stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/account/addresses/" + id).cookie(stranger).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/addresses").cookie(stranger)).andExpect(jsonPath("$", hasSize(0)));
        // Still there for the owner.
        mvc.perform(get("/api/v1/account/addresses").cookie(owner)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theAddressBookIsLimitedAndNeedsALogin() throws Exception {
        mvc.perform(get("/api/v1/account/addresses")).andExpect(status().isUnauthorized());

        Cookie session = auth.registeredSession(uniqueEmail());
        for (int i = 0; i < 10; i++) {
            create(session, ADDRESS);
        }
        mvc.perform(json(post("/api/v1/account/addresses"), ADDRESS).cookie(session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }

    private String create(Cookie session, String body) throws Exception {
        String response = mvc.perform(json(post("/api/v1/account/addresses"), body).cookie(session))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }
}
