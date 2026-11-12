package com.pwaquatics.shop.catalog.application.admin;

/** @param slug null or blank: derived from the name */
public record GradeInput(String name, String slug, String scale, String description, int rank, int sortOrder) {}
