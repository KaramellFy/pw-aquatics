package com.pwaquatics.shop.common.web;

import com.pwaquatics.shop.common.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Full links to pages of the shop, for emails and page metadata. German pages live under "/de" with the same
 * path words as the English ones (docs/adr/012-language-in-links.md), so a German customer's
 * email links open the shop in German.
 */
@Component
public class SiteLinks {

    private final String baseUrl;

    public SiteLinks(AppProperties properties) {
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    /**
     * @param locale the customer's language ("en" or "de"; anything else gives English)
     * @param path a shop path starting with "/", e.g. "/account/orders/PW-2027-000123"
     */
    public String page(String locale, String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Shop paths start with '/'");
        }
        return baseUrl + ("de".equals(locale) ? "/de" : "") + path;
    }

    /**
     * Full link to a file of the shop, e.g. a photo under "/media"; links that are already full
     * are returned as they are.
     */
    public String file(String path) {
        if (path.startsWith("https://") || path.startsWith("http://")) {
            return path;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Shop paths start with '/'");
        }
        return baseUrl + path;
    }
}
