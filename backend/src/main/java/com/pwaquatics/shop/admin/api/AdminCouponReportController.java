package com.pwaquatics.shop.admin.api;

import com.pwaquatics.shop.admin.application.AdminCouponReports;
import com.pwaquatics.shop.admin.application.view.CouponRedemptionView;
import com.pwaquatics.shop.admin.application.view.CouponUsageView;
import com.pwaquatics.shop.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Figures and orders of one coupon (docs/API.md §3.6). Role ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/coupons/{id}")
@Tag(name = "Admin: coupons")
class AdminCouponReportController {

    private final AdminCouponReports reports;

    AdminCouponReportController(AdminCouponReports reports) {
        this.reports = reports;
    }

    @Operation(summary = "Orders that used the coupon, what it saved customers and those orders' revenue")
    @GetMapping("/usage")
    CouponUsageView usage(@PathVariable UUID id) {
        return reports.usage(id);
    }

    @Operation(summary = "Orders that used the coupon, newest first")
    @GetMapping("/redemptions")
    PageResponse<CouponRedemptionView> redemptions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.redemptions(id, PageRequest.of(page, size));
    }
}
