package com.pwaquatics.shop.catalog.application.admin;

/** @param slug null or blank: derived from the name */
public record ColourInput(String name, String slug, String hexCode, int sortOrder) {}
