package com.pwaquatics.shop.admin.api;

import com.pwaquatics.shop.admin.application.AdminStatisticsService;
import com.pwaquatics.shop.admin.application.view.DashboardView;
import com.pwaquatics.shop.admin.application.view.ProductStatsView;
import com.pwaquatics.shop.admin.application.view.SalesStatsView;
import com.pwaquatics.shop.admin.application.view.StockStatsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard and sales statistics (docs/API.md §3.1). Role ADMIN only.
 *
 * <p>Periods are given as {@code from} and {@code to} (ISO dates, both inclusive, shop time
 * zone). Without them the last 30 days up to today are used.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: statistics")
class AdminStatisticsController {

    private static final int DEFAULT_DAYS = 30;

    private final AdminStatisticsService statistics;

    AdminStatisticsController(AdminStatisticsService statistics) {
        this.statistics = statistics;
    }

    @Operation(summary = "What needs attention now: open orders, low stock, latest orders")
    @GetMapping("/dashboard")
    DashboardView dashboard() {
        return statistics.dashboard();
    }

    @Operation(summary = "Sales figures, time series and breakdowns of a period, compared with the period before")
    @GetMapping("/stats/sales")
    SalesStatsView sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : statistics.today();
        return statistics.sales(from != null ? from : defaultFrom(end), end);
    }

    @Operation(summary = "Best sellers and revenue by category and species")
    @GetMapping("/stats/products")
    ProductStatsView products(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        LocalDate end = to != null ? to : statistics.today();
        return statistics.products(from != null ? from : defaultFrom(end), end, limit);
    }

    @Operation(summary = "Restocks, sales and losses from the stock ledger")
    @GetMapping("/stats/stock")
    StockStatsView stock(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : statistics.today();
        return statistics.stock(from != null ? from : defaultFrom(end), end);
    }

    @Operation(summary = "Daily sales of a period as CSV (no personal data)")
    @GetMapping(value = "/stats/sales/export", produces = "text/csv")
    ResponseEntity<String> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : statistics.today();
        LocalDate start = from != null ? from : defaultFrom(end);
        String csv = statistics.salesCsv(start, end);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("pw-aquatics-sales-%s-to-%s.csv".formatted(start, end))
                        .build()
                        .toString())
                .body(csv);
    }

    private static LocalDate defaultFrom(LocalDate end) {
        return end.minusDays(DEFAULT_DAYS - 1);
    }
}
