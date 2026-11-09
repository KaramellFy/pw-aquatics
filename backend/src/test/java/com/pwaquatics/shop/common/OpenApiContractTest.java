package com.pwaquatics.shop.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The frontend's generated types depend on these OpenAPI conventions. */
@IntegrationTest
class OpenApiContractTest {

    @Autowired MockMvc mvc;

    @Test
    void responseFieldsAreRequiredAndNullableOnesAreMarked() throws Exception {
        mvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProductSummaryView.required",
                        Matchers.hasItems("slug", "grade", "priceFrom", "availability")))
                .andExpect(jsonPath("$.components.schemas.ProductSummaryView.properties.grade.anyOf[1].type")
                        .value("null"))
                .andExpect(jsonPath("$.components.schemas.ProductSummaryView.properties.category.$ref")
                        .value("#/components/schemas/CategoryRef"))
                .andExpect(jsonPath("$.components.schemas.ColourView.properties.hexCode.type",
                        Matchers.contains("string", "null")))
                // Request bodies keep validation-based required flags (orderNumber is optional).
                .andExpect(jsonPath("$.components.schemas.ContactMessageRequest.required",
                        Matchers.not(Matchers.hasItem("orderNumber"))));
    }
}
