package com.pwaquatics.shop.admin.application.view;

/**
 * Headline figures of a period. Sales are orders that are neither cancelled (expired unpaid
 * orders are cancelled too) nor refunded; amounts are gross, including VAT and shipping.
 *
 * @param revenue value of all sales
 * @param paidRevenue the part of {@code revenue} that has been paid
 * @param orders number of sales
 * @param averageOrderValue {@code revenue / orders}
 * @param animalsSold live animals in those orders (packs × animals per pack)
 * @param newAccounts customer accounts registered in the period
 * @param returningCustomerShare share of orders from customers who had ordered before
 * @param cancelledOrders orders placed in the period that are now cancelled
 * @param cancellationRate {@code cancelledOrders} as a share of all orders placed
 */
public record SalesKpisView(
        MoneyKpiView revenue,
        MoneyKpiView paidRevenue,
        CountKpiView orders,
        MoneyKpiView averageOrderValue,
        CountKpiView animalsSold,
        CountKpiView newAccounts,
        ShareKpiView returningCustomerShare,
        CountKpiView cancelledOrders,
        ShareKpiView cancellationRate) {}
