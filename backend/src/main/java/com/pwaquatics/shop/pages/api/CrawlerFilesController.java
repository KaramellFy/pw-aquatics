package com.pwaquatics.shop.pages.api;

import com.pwaquatics.shop.pages.application.CrawlerFiles;
import com.pwaquatics.shop.pages.application.ShellSource.ShellUnavailableException;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /sitemap.xml} and {@code /robots.txt} for search engines
 * (docs/adr/013-pages-filled-in-by-the-backend.md). Not part of the JSON API: the frontend's
 * Caddy forwards both here as {@code /_site/…} and serves its own {@code robots.txt} if this
 * answers 5xx or not at all.
 */
@Hidden
@RestController
@RequestMapping("/_site")
class CrawlerFilesController {

    private static final MediaType XML = new MediaType("application", "xml", StandardCharsets.UTF_8);
    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final CrawlerFiles files;

    CrawlerFilesController(CrawlerFiles files) {
        this.files = files;
    }

    @GetMapping("/sitemap.xml")
    ResponseEntity<String> sitemap() {
        // New products should show up at the next visit.
        return ResponseEntity.ok().contentType(XML).cacheControl(CacheControl.noCache()).body(files.sitemap());
    }

    @GetMapping("/robots.txt")
    ResponseEntity<String> robots() {
        try {
            return ResponseEntity.ok().contentType(TEXT).cacheControl(CacheControl.noCache()).body(files.robots());
        } catch (ShellUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .cacheControl(CacheControl.noStore())
                    .body("robots.txt unavailable");
        }
    }
}
