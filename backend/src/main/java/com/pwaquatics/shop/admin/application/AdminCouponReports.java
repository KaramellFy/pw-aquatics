package com.pwaquatics.shop.admin.application;

import com.pwaquatics.shop.admin.application.view.CouponRedemptionView;
import com.pwaquatics.shop.admin.application.view.CouponUsageView;
import com.pwaquatics.shop.admin.infrastructure.CouponQueries;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.domain.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What one coupon has brought, and the orders that used it (docs/API.md §3.6). Read only. */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminCouponReports {

    private final CouponQueries queries;

    AdminCouponReports(CouponQueries queries) {
        this.queries = queries;
    }

    /** Orders that used the coupon and count as sales, what it saved and their revenue. */
    public CouponUsageView usage(UUID couponId) {
        CouponQueries.Usage usage = queries.usage(couponId);
        return new CouponUsageView(usage.orders(), Money.eur(usage.savingCents()), Money.eur(usage.revenueCents()));
    }

    /** Orders that used the coupon, newest first, cancelled ones included (marked as released). */
    public PageResponse<CouponRedemptionView> redemptions(UUID couponId, PageRequest page) {
        List<CouponRedemptionView> rows = queries.redemptions(couponId, page.getPageSize(), page.getOffset()).stream()
                .map(row -> new CouponRedemptionView(
                        row.orderNumber(),
                        row.placedAt(),
                        row.customerName().isBlank() ? null : row.customerName(),
                        OrderStatus.valueOf(row.orderStatus()),
                        Money.eur(row.orderTotalCents()),
                        Money.eur(row.savingCents()),
                        row.released()))
                .toList();
        return PageResponse.of(new PageImpl<>(rows, page, queries.redemptionCount(couponId)));
    }
}
