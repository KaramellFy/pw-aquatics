package com.pwaquatics.shop.pages.api;

import com.pwaquatics.shop.pages.application.PageService;
import com.pwaquatics.shop.pages.application.RenderedPage;
import com.pwaquatics.shop.pages.application.ShellSource.ShellUnavailableException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pages of the shop for the first page load, filled in by the backend
 * (docs/adr/013-pages-filled-in-by-the-backend.md). Not part of the JSON API: the frontend's
 * Caddy forwards every page request here as {@code /_pages/<path>} and serves the plain
 * {@code index.html} if this answers 5xx or not at all.
 */
@Hidden
@RestController
class PageController {

    static final String PREFIX = "/_pages";
    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final PageService pages;

    PageController(PageService pages) {
        this.pages = pages;
    }

    @GetMapping({PREFIX, PREFIX + "/**"})
    ResponseEntity<String> page(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length() + PREFIX.length());
        try {
            RenderedPage page = pages.render(path, request.getQueryString());
            return ResponseEntity.status(page.status())
                    .contentType(HTML)
                    // Prices and stock change all the time: browsers check again on every visit.
                    .cacheControl(CacheControl.noCache())
                    .body(page.html());
        } catch (ShellUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .cacheControl(CacheControl.noStore())
                    .body("Page template unavailable");
        }
    }
}
