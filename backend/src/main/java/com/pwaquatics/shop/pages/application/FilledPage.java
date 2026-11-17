package com.pwaquatics.shop.pages.application;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * A filled-in page before it is put into the template.
 *
 * @param head null for pages that are not filled in (they keep the template's title)
 * @param body the page's text, or null
 * @param data API responses by the path the app would request them with ("/products/x")
 */
record FilledPage(int status, @Nullable PageHead head, @Nullable String body, Map<String, Object> data) {

    /** Pages the backend does not fill in (account, cart, checkout, admin, …): only the language is set. */
    static FilledPage plain() {
        return new FilledPage(200, null, null, Map.of());
    }
}
