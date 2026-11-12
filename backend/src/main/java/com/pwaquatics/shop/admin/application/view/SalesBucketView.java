package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import java.time.LocalDate;

/**
 * One point of the sales time series, split into paid orders and orders still awaiting
 * payment.
 *
 * @param start first day of the bucket (the day, the week's Monday or the month's first day)
 * @param revenue {@code paidRevenue + awaitingRevenue}
 * @param orders {@code paidOrders + awaitingOrders}
 * @param animals {@code paidAnimals + awaitingAnimals}
 */
public record SalesBucketView(
        LocalDate start,
        Money revenue,
        long orders,
        long animals,
        Money paidRevenue,
        Money awaitingRevenue,
        long paidOrders,
        long awaitingOrders,
        long paidAnimals,
        long awaitingAnimals) {}
