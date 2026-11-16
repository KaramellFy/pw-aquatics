package com.pwaquatics.shop.catalog.application.admin;

/** @param slug null or blank: derived from the name */
/** @param nameDe the German name, or null */
public record ColourInput(String name, String slug, String hexCode, int sortOrder, String nameDe) {}
