package com.pwaquatics.shop.catalog.application.admin;

import java.util.UUID;

/** @param slug null or blank: derived from the name */
public record CategoryInput(
        String name, String slug, UUID parentId, String description, int sortOrder, boolean active) {}
