package com.pwaquatics.shop.pages.application;

/** A page as served: its HTTP status (200, or 404 for an unknown product) and its HTML. */
public record RenderedPage(int status, String html) {}
