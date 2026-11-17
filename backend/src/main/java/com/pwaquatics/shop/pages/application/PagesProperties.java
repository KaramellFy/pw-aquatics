package com.pwaquatics.shop.pages.application;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Pages filled in by the backend (docs/adr/013-pages-filled-in-by-the-backend.md).
 *
 * @param shellUrl where the built frontend is served inside the deployment, e.g.
 *     {@code http://frontend:8080/}; its {@code index.html} and {@code pages.json} are the
 *     template of every page. Empty: pages are not filled in (local development with Vite).
 * @param timeout how long to wait for the frontend before serving the last known template
 */
@Validated
@ConfigurationProperties("app.pages")
public record PagesProperties(@Nullable URI shellUrl, @NotNull Duration timeout) {

    public boolean isEnabled() {
        return shellUrl != null && !shellUrl.toString().isBlank();
    }

    @AssertTrue(message = "app.pages.shell-url must be an http(s) URL ending in '/'")
    public boolean isShellUrlValid() {
        if (!isEnabled()) {
            return true;
        }
        String url = shellUrl.toString();
        return (url.startsWith("http://") || url.startsWith("https://")) && url.endsWith("/");
    }
}
