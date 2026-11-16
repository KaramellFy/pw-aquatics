package com.pwaquatics.shop.catalog.application.admin;

/** @param slug null or blank: derived from the name */
/** @param nameDe German texts ({@code …De}) are null where not translated */
public record GradeInput(
        String name, String slug, String scale, String description, int rank, int sortOrder, String nameDe,
        String descriptionDe) {}
