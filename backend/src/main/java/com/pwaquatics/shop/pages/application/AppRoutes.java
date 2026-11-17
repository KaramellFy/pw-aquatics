package com.pwaquatics.shop.pages.application;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The addresses the app has pages for, from the frontend build ({@code pages.json}, written
 * from {@code frontend/src/app/routePaths.ts}). Patterns work like the app's router: ":name"
 * matches one segment, a final "*" the rest, and words match regardless of case.
 */
public final class AppRoutes {

    private final List<String[]> patterns;

    private AppRoutes(List<String[]> patterns) {
        this.patterns = patterns;
    }

    /**
     * @param routes a JSON array of patterns such as "/products/:slug"; without one, every
     *     address counts as known (a build without the list must not turn pages into 404s)
     */
    public static AppRoutes of(JsonNode routes) {
        if (routes == null || !routes.isArray() || routes.isEmpty()) {
            return new AppRoutes(null);
        }
        List<String[]> patterns = new ArrayList<>();
        for (JsonNode route : routes) {
            if (!route.isString() || !route.asString().startsWith("/")) {
                throw new IllegalArgumentException("Routes in pages.json start with '/'");
            }
            patterns.add(segments(route.asString()));
        }
        return new AppRoutes(List.copyOf(patterns));
    }

    /** @param path an address without language prefix, e.g. "/account/orders" */
    public boolean knows(String path) {
        if (patterns == null) {
            return true;
        }
        String[] segments = segments(path);
        return patterns.stream().anyMatch(pattern -> matches(pattern, segments));
    }

    private static boolean matches(String[] pattern, String[] segments) {
        for (int i = 0; i < pattern.length; i++) {
            if ("*".equals(pattern[i]) && i == pattern.length - 1) {
                return true;
            }
            if (i >= segments.length) {
                return false;
            }
            boolean any = pattern[i].startsWith(":");
            if (!any && !pattern[i].equalsIgnoreCase(segments[i])) {
                return false;
            }
        }
        return pattern.length == segments.length;
    }

    private static String[] segments(String path) {
        String trimmed = path.replaceAll("^/+|/+$", "");
        return trimmed.isEmpty() ? new String[0] : trimmed.split("/+");
    }
}
