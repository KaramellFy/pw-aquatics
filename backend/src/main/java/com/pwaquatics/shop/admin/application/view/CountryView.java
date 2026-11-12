package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;

/** Sales shipped to one country (ISO 3166-1 alpha-2). */
public record CountryView(String countryCode, long orders, Money revenue) {}
