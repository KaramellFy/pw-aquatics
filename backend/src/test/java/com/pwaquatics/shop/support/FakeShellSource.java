package com.pwaquatics.shop.support;

import com.pwaquatics.shop.pages.application.PageShell;
import com.pwaquatics.shop.pages.application.ShellSource;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

/**
 * Stands in for the frontend container in integration tests: a template shaped like the built
 * {@code index.html}, and a {@code pages.json} with some of the app's routes, a few page
 * texts in both languages and robots rules.
 */
@Primary
public class FakeShellSource implements ShellSource {

    public static final String TEMPLATE = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <title>PW Aquatics</title>
                <meta
                  name="description"
                  content="Default description."
                />
                <script type="module" crossorigin src="/assets/index-abc.js"></script>
                <link rel="stylesheet" crossorigin href="/assets/index-abc.css">
              </head>
              <body>
                <div id="root"></div>
              </body>
            </html>
            """;

    public static final String TEXTS = """
            {
              "en": {
                "brand": "PW Aquatics", "backToHome": "Back to home",
                "nav": {"main": "Main navigation", "shop": "Shop", "gallery": "Gallery", "about": "About", "contact": "Contact"},
                "pages": {
                  "home": {"title": "PW Aquatics – Dwarf shrimp", "description": "Home description", "heading": "Dwarf shrimp", "lead": "From our tanks."},
                  "shop": {"title": "Shop – PW Aquatics", "description": "Shop description", "heading": "All animals", "lead": "Healthy animals."},
                  "about": {"title": "About – PW Aquatics", "description": "About description", "heading": "Why we sell shrimp", "lead": "Our story."},
                  "gallery": {"title": "Gallery – PW Aquatics", "description": "Gallery description", "heading": "Impressions", "lead": "Colours."},
                  "contact": {"title": "Contact – PW Aquatics", "description": "Contact description", "heading": "Write to us", "lead": "We reply."}
                },
                "notFound": {"title": "Page not found – PW Aquatics", "heading": "This page has drifted away", "text": "It does not exist."},
                "product": {"from": "from {{price}}", "availability": {"IN_STOCK": "In stock", "LOW_STOCK": "Only a few left", "COMING_SOON": "Coming soon", "OUT_OF_STOCK": "Sold out"}},
                "species": {"available": "Available now"},
                "pagination": {"label": "Pagination", "previous": "Previous", "next": "Next"}
              },
              "de": {
                "brand": "PW Aquatics", "backToHome": "Zur Startseite",
                "nav": {"main": "Hauptnavigation", "shop": "Shop", "gallery": "Galerie", "about": "Über uns", "contact": "Kontakt"},
                "pages": {
                  "about": {"title": "Über uns – PW Aquatics", "description": "Über uns Beschreibung", "heading": "Warum wir Garnelen verkaufen", "lead": "Unsere Geschichte."}
                },
                "product": {"from": "ab {{price}}", "availability": {"IN_STOCK": "Auf Lager", "LOW_STOCK": "Nur noch wenige", "COMING_SOON": "Bald verfügbar", "OUT_OF_STOCK": "Ausverkauft"}}
              }
            }
            """;

    /** A part of frontend/src/app/routePaths.ts. */
    public static final String ROUTES = """
            ["/", "/about", "/gallery", "/contact", "/legal/:page", "/shop", "/shop/:categorySlug",
             "/products/:slug", "/species/:slug", "/login", "/account", "/account/orders",
             "/account/orders/:orderNumber", "/cart", "/checkout", "/admin", "/admin/*"]
            """;

    /** frontend/public/robots.txt, which the build copies into {@code pages.json}. */
    public static final String ROBOTS = "User-agent: *\nDisallow: /account\nDisallow: /de/account\n";

    /** {@code pages.json} as the frontend build writes it. */
    public static final String PAGES = "{\"routes\": " + ROUTES + ", \"texts\": " + TEXTS
            + ", \"robots\": \"" + ROBOTS.replace("\n", "\\n") + "\"}";

    private final PageShell shell = shell();
    private volatile boolean unavailable;

    /** The template with these routes, texts and robots rules. */
    public static PageShell shell() {
        return PageShell.parse(TEMPLATE, new ObjectMapper().readTree(PAGES));
    }

    @Override
    public PageShell current() {
        if (unavailable) {
            throw new ShellUnavailableException("Test: no template.");
        }
        return shell;
    }

    /** Simulates a deployment whose frontend was never reached. */
    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }
}
