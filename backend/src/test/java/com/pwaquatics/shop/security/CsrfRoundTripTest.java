package com.pwaquatics.shop.security;

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
 * The CSRF flow exactly as the SPA uses it, against a real server: fetch the cookie,
 * then send its value back in the X-XSRF-TOKEN header.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CsrfRoundTripTest {

    private static final String BODY = """
            {"name":"Anna","email":"anna@example.com","subject":"Hello",
             "message":"A message that is long enough to be accepted."}
            """;

    @LocalServerPort int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void spaCanObtainAndUseTheCsrfToken() throws Exception {
        HttpResponse<Void> csrf = http.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.discarding());

        assertThat(csrf.statusCode()).isEqualTo(204);
        String setCookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).startsWith("XSRF-TOKEN=").contains("Path=/").contains("SameSite=Lax")
                .doesNotContainIgnoringCase("HttpOnly");
        String token = setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));

        HttpResponse<String> withToken = http.send(post(token, true), HttpResponse.BodyHandlers.ofString());
        assertThat(withToken.statusCode()).isEqualTo(202);

        HttpResponse<String> withoutHeader = http.send(post(token, false), HttpResponse.BodyHandlers.ofString());
        assertThat(withoutHeader.statusCode()).isEqualTo(403);
        assertThat(withoutHeader.body()).contains("\"code\":\"CSRF_INVALID\"");
    }

    private HttpRequest post(String token, boolean sendHeader) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri("/api/v1/contact"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .POST(HttpRequest.BodyPublishers.ofString(BODY));
        if (sendHeader) {
            builder.header("X-XSRF-TOKEN", token);
        }
        return builder.build();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
