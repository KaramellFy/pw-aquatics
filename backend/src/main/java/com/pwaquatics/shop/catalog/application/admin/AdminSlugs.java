package com.pwaquatics.shop.catalog.application.admin;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.text.Slugs;
import java.util.function.Predicate;

/** Slug rules shared by all admin forms. */
final class AdminSlugs {

    private AdminSlugs() {}

    /**
     * The slug to store: the given one, or one derived from {@code name} when none is given.
     *
     * @param taken whether a slug is already used by another record
     * @throws BusinessRuleException {@code SLUG_INVALID} for a malformed or too long slug
     * @throws ConflictException {@code SLUG_TAKEN} when another record already uses it
     */
    static String resolve(String requested, String name, int maxLength, Predicate<String> taken) {
        String slug = requested == null || requested.isBlank() ? Slugs.of(name, maxLength) : requested.strip();
        if (!Slugs.isValid(slug) || slug.length() > maxLength) {
            throw new BusinessRuleException(
                    "SLUG_INVALID", "Use lower-case letters, digits and hyphens for the web address.");
        }
        if (taken.test(slug)) {
            throw new ConflictException("SLUG_TAKEN", "This web address is already used.");
        }
        return slug;
    }

    /**
     * A free slug based on {@code base}: {@code base}, then {@code base-2}, {@code base-3} …
     * (used when copying a product).
     */
    static String firstFree(String base, int maxLength, Predicate<String> taken) {
        String candidate = base.length() > maxLength ? base.substring(0, maxLength) : base;
        for (int n = 2; taken.test(candidate); n++) {
            String suffix = "-" + n;
            String stem = base.length() + suffix.length() > maxLength
                    ? base.substring(0, maxLength - suffix.length())
                    : base;
            candidate = stem + suffix;
        }
        return candidate;
    }
}
