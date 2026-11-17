package com.pwaquatics.shop.pages.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pwaquatics.shop.catalog.domain.Availability;
import org.junit.jupiter.api.Test;

class PageServiceTest {

    @Test
    void readsTheListPageOnlyFromThePlainList() {
        assertThat(PageService.listPage(null)).isEqualTo(1);
        assertThat(PageService.listPage("")).isEqualTo(1);
        assertThat(PageService.listPage("page=3")).isEqualTo(3);
        assertThat(PageService.listPage("utm_source=news&page=3")).isEqualTo(3);
        // Filtered, searched or sorted views point to the plain list's first page.
        assertThat(PageService.listPage("colour=t-blue&page=3")).isEqualTo(1);
        assertThat(PageService.listPage("page=3&q=blue%20dream")).isEqualTo(1);
        assertThat(PageService.listPage("sort=price-asc&page=3")).isEqualTo(1);
        // Empty filters are no filters (the app drops them too).
        assertThat(PageService.listPage("colour=&page=2")).isEqualTo(2);
        // Not a page number.
        assertThat(PageService.listPage("page=0")).isEqualTo(1);
        assertThat(PageService.listPage("page=-2")).isEqualTo(1);
        assertThat(PageService.listPage("page=two")).isEqualTo(1);
        assertThat(PageService.listPage("page=99999999")).isEqualTo(1);
    }

    @Test
    void offersEachPackSizeAsItCanBeBought() {
        assertThat(PageService.offerAvailability(Availability.IN_STOCK, true))
                .isEqualTo("https://schema.org/InStock");
        assertThat(PageService.offerAvailability(Availability.LOW_STOCK, true))
                .isEqualTo("https://schema.org/LimitedAvailability");
        assertThat(PageService.offerAvailability(Availability.LOW_STOCK, false))
                .isEqualTo("https://schema.org/OutOfStock");
        assertThat(PageService.offerAvailability(Availability.COMING_SOON, false))
                .isEqualTo("https://schema.org/OutOfStock");
    }
}
