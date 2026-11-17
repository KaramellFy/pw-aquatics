package com.pwaquatics.shop.pages.infrastructure;

import com.pwaquatics.shop.pages.application.AppRoutes;
import com.pwaquatics.shop.pages.application.PageShell;
import com.pwaquatics.shop.pages.application.PageTexts;
import com.pwaquatics.shop.pages.application.PagesProperties;
import com.pwaquatics.shop.pages.application.ShellSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads the page template ({@code index.html}) and the app's routes and page texts
 * ({@code pages.json}) from the frontend container ({@code app.pages.shell-url}).
 *
 * <p>The template is checked again at most once a second with a conditional request, so a
 * newly deployed frontend is picked up at once and the page never points at scripts of an
 * older build. If the frontend cannot be reached, the last template is used; without one, pages
 * are not filled in and the edge proxy serves the plain {@code index.html}.
 */
@Component
class FrontendShellSource implements ShellSource {

    private static final Logger log = LoggerFactory.getLogger(FrontendShellSource.class);
    private static final Duration RECHECK = Duration.ofSeconds(1);

    private final PagesProperties properties;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http;

    private volatile Loaded loaded;
    private volatile boolean failing;

    FrontendShellSource(PagesProperties properties, ObjectMapper json, Clock clock) {
        this.properties = properties;
        this.json = json;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public PageShell current() {
        if (!properties.isEnabled()) {
            throw new ShellUnavailableException("Pages are not filled in (app.pages.shell-url is not set).");
        }
        Loaded known = loaded;
        if (known != null && clock.instant().isBefore(known.checkedAt().plus(RECHECK))) {
            return known.shell();
        }
        synchronized (this) {
            known = loaded;
            if (known != null && clock.instant().isBefore(known.checkedAt().plus(RECHECK))) {
                return known.shell();
            }
            try {
                loaded = refresh(known);
                if (failing) {
                    log.info("The page template can be loaded again.");
                    failing = false;
                }
                return loaded.shell();
            } catch (IOException | RuntimeException e) {
                return fallBack(known, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fallBack(known, e);
            }
        }
    }

    private PageShell fallBack(Loaded known, Exception cause) {
        if (!failing) {
            log.warn("Could not load the page template from {}: {}", properties.shellUrl(), cause.toString());
            failing = true;
        }
        if (known == null) {
            throw new ShellUnavailableException("No page template yet.", cause);
        }
        // Keep serving the last template; try the frontend again after the usual pause.
        loaded = known.checkedAgainAt(clock.instant());
        return known.shell();
    }

    private Loaded refresh(Loaded known) throws IOException, InterruptedException {
        HttpRequest.Builder request = get("index.html");
        if (known != null && known.etag() != null) {
            request.header("If-None-Match", known.etag());
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        Instant now = clock.instant();
        if (response.statusCode() == 304 && known != null) {
            return known.checkedAgainAt(now);
        }
        if (response.statusCode() != 200) {
            throw new IOException("index.html answered " + response.statusCode());
        }
        JsonNode pages = pages();
        PageShell shell = PageShell.parse(response.body(), PageTexts.of(pages.path("texts")), AppRoutes.of(pages.path("routes")));
        String etag = response.headers().firstValue("ETag").orElse(null);
        log.info("Loaded the page template (ETag {}).", etag);
        return new Loaded(shell, etag, now);
    }

    private JsonNode pages() throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(get("pages.json").build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("pages.json answered " + response.statusCode());
        }
        try {
            return json.readTree(response.body());
        } catch (JacksonException e) {
            throw new IOException("pages.json is not valid JSON", e);
        }
    }

    private HttpRequest.Builder get(String file) {
        URI uri = properties.shellUrl().resolve(file);
        return HttpRequest.newBuilder(uri).timeout(properties.timeout()).GET();
    }

    private record Loaded(PageShell shell, String etag, Instant checkedAt) {
        Loaded checkedAgainAt(Instant now) {
            return new Loaded(shell, etag, now);
        }
    }
}
