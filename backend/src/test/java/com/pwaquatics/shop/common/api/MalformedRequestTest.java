package com.pwaquatics.shop.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Requests the server cannot read are the caller's mistake: 400, never a server error. Runs
 * against a real server, because MockMvc does not parse query strings the way Tomcat does.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MalformedRequestTest {

    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void parametersWithoutANameAreRejectedAsMalformed() throws Exception {
        // Found by the ZAP API scan (docs/CHECKS.md): these answered 500 before.
        for (String path : new String[] {"/api/v1/products?=x&size=10", "/api/v1/gallery?page=0&=", "/api/v1/products/facets?="}) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path).isEqualTo(400);
            assertThat(response.body()).as(path).contains("\"code\":\"MALFORMED_REQUEST\"").doesNotContain("Exception");
        }
    }
}
