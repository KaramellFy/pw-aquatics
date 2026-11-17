package com.pwaquatics.shop.catalog.application.view;

import java.util.List;

/** The catalog pages search engines may list: visible products, active categories, species. */
public record CatalogSitemap(List<SitemapEntry> products, List<SitemapEntry> categories, List<SitemapEntry> species) {}
