package com.pwaquatics.shop.admin.application.view;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;

/** Orders placed in the period with this current status. */
public record StatusCountView(OrderStatus status, long orders, Money total) {}
