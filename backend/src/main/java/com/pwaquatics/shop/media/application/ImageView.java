package com.pwaquatics.shop.media.application;

/** A responsive image as returned by the API (docs/API.md §2.2). */
public record ImageView(String url, String srcSet, String alt, int width, int height) {}
