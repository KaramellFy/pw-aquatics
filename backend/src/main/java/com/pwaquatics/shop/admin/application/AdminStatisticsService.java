package com.pwaquatics.shop.admin.application;

import static com.pwaquatics.shop.admin.domain.Ratios.averageCents;
import static com.pwaquatics.shop.admin.domain.Ratios.changeBps;
import static com.pwaquatics.shop.admin.domain.Ratios.shareBps;

import com.pwaquatics.shop.admin.application.view.CountKpiView;
import com.pwaquatics.shop.admin.application.view.CountryView;
import com.pwaquatics.shop.admin.application.view.DashboardView;
import com.pwaquatics.shop.admin.application.view.GroupSalesView;
import com.pwaquatics.shop.admin.application.view.LowStockProductView;
import com.pwaquatics.shop.admin.application.view.MoneyKpiView;
import com.pwaquatics.shop.admin.application.view.ProductSalesView;
import com.pwaquatics.shop.admin.application.view.ProductStatsView;
import com.pwaquatics.shop.admin.application.view.RecentOrderView;
import com.pwaquatics.shop.admin.application.view.SalesBucketView;
import com.pwaquatics.shop.admin.application.view.SalesKpisView;
import com.pwaquatics.shop.admin.application.view.SalesStatsView;
import com.pwaquatics.shop.admin.application.view.ShareKpiView;
import com.pwaquatics.shop.admin.application.view.StatsPeriodView;
import com.pwaquatics.shop.admin.application.view.StatusCountView;
import com.pwaquatics.shop.admin.application.view.StockReasonView;
import com.pwaquatics.shop.admin.application.view.StockStatsView;
import com.pwaquatics.shop.admin.application.view.WeekdayView;
import com.pwaquatics.shop.admin.domain.StatsPeriod;
import com.pwaquatics.shop.admin.infrastructure.StatisticsQueries;
import com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.BucketRow;
import com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.DayRow;
import com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.ReasonRow;
import com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.SalesTotals;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.inventory.domain.StockMovementReason;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.shipping.application.ShippingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard and sales statistics for the shop owner (docs/API.md §3.1). Read only.
 *
 * <p>Periods are whole days in the shop's time zone. A sale is an order that is neither
 * cancelled nor refunded, counted on the day it was placed; amounts are gross. Every figure,
 * share and change is calculated here, so the admin pages only format what they receive.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
public class AdminStatisticsService {

    /** Shop time zone for days, weeks and weekdays. */
    static final ZoneId ZONE = ShippingService.SHOP_ZONE;

    private static final int RECENT_ORDERS = 8;
    private static final int LOW_STOCK_ROWS = 8;

    private final StatisticsQueries queries;
    private final Clock clock;

    AdminStatisticsService(StatisticsQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /** Today in the shop's time zone, the default end of a period. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** What needs attention now: open orders, stock running out, the latest orders. */
    public DashboardView dashboard() {
        StatisticsQueries.OpenOrders open = queries.openOrders();
        StatisticsQueries.ProductCounts products = queries.productCounts();
        List<RecentOrderView> recent = queries.recentOrders(RECENT_ORDERS).stream()
                .map(row -> new RecentOrderView(row.orderNumber(), row.placedAt(), OrderStatus.valueOf(row.status()),
                        PaymentStatus.valueOf(row.paymentStatus()), Money.eur(row.totalCents()),
                        row.firstName() == null ? null : row.firstName() + " " + row.lastName(), row.countryCode(),
                        row.animals()))
                .toList();
        List<LowStockProductView> lowStock = queries.lowStockProducts(LOW_STOCK_ROWS).stream()
                .map(row -> new LowStockProductView(row.id(), row.name(), row.stockUnits(), row.lowStockThreshold()))
                .toList();
        return new DashboardView(open.awaitingPayment(), Money.eur(open.awaitingPaymentCents()), open.toShip(),
                products.live(), products.lowStock(), products.soldOut(), recent, lowStock);
    }

    /**
     * Headline figures compared with the previous period, the sales time series and sales by
     * status, weekday and destination country.
     *
     * @throws com.pwaquatics.shop.common.error.BusinessRuleException {@code STATS_RANGE_INVALID}
     */
    public SalesStatsView sales(LocalDate from, LocalDate to) {
        StatsPeriod period = StatsPeriod.of(from, to);
        StatsPeriod previous = period.previous();
        Instant start = period.start(ZONE);
        Instant end = period.end(ZONE);

        SalesTotals now = queries.salesTotals(start, end);
        SalesTotals before = queries.salesTotals(previous.start(ZONE), previous.end(ZONE));
        long accounts = queries.newAccounts(start, end);
        long accountsBefore = queries.newAccounts(previous.start(ZONE), previous.end(ZONE));
        SalesKpisView kpis = new SalesKpisView(
                money(now.revenueCents(), before.revenueCents()),
                money(now.paidRevenueCents(), before.paidRevenueCents()),
                count(now.orders(), before.orders()),
                money(averageCents(now.revenueCents(), now.orders()), averageCents(before.revenueCents(), before.orders())),
                count(now.animals(), before.animals()),
                count(accounts, accountsBefore),
                new ShareKpiView(shareBps(now.returningOrders(), now.orders()),
                        shareBps(before.returningOrders(), before.orders())),
                count(now.cancelledOrders(), before.cancelledOrders()),
                new ShareKpiView(shareBps(now.cancelledOrders(), now.placedOrders()),
                        shareBps(before.cancelledOrders(), before.placedOrders())));

        List<StatusCountView> byStatus = queries.ordersByStatus(start, end).stream()
                .map(row -> new StatusCountView(OrderStatus.valueOf(row.status()), row.orders(),
                        Money.eur(row.totalCents())))
                .sorted(Comparator.comparingLong(StatusCountView::orders).reversed()
                        .thenComparing(StatusCountView::status))
                .toList();
        Map<Integer, StatisticsQueries.WeekdayRow> weekdays = queries.salesByWeekday(start, end, ZONE).stream()
                .collect(Collectors.toMap(StatisticsQueries.WeekdayRow::isoWeekday, Function.identity()));
        List<WeekdayView> byWeekday = IntStream.rangeClosed(1, 7)
                .mapToObj(day -> {
                    StatisticsQueries.WeekdayRow row = weekdays.get(day);
                    return row == null
                            ? new WeekdayView(day, 0, Money.eur(0))
                            : new WeekdayView(day, row.orders(), Money.eur(row.revenueCents()));
                })
                .toList();
        List<CountryView> byCountry = queries.salesByCountry(start, end).stream()
                .map(row -> new CountryView(row.countryCode(), row.orders(), Money.eur(row.revenueCents())))
                .toList();

        return new SalesStatsView(periodView(period), kpis, series(period, start, end), byStatus, byWeekday, byCountry);
    }

    /**
     * Best sellers and revenue by category and species.
     *
     * @param limit number of best sellers (1–50)
     */
    public ProductStatsView products(LocalDate from, LocalDate to, int limit) {
        StatsPeriod period = StatsPeriod.of(from, to);
        Instant start = period.start(ZONE);
        Instant end = period.end(ZONE);
        List<ProductSalesView> top = queries.topProducts(start, end, limit).stream()
                .map(row -> new ProductSalesView(row.productId(), row.name(), row.gradeName(), row.units(), row.packs(),
                        Money.eur(row.revenueCents())))
                .toList();
        Function<StatisticsQueries.GroupRow, GroupSalesView> group =
                row -> new GroupSalesView(row.name(), row.units(), Money.eur(row.revenueCents()));
        return new ProductStatsView(periodView(period), top,
                queries.salesByCategory(start, end).stream().map(group).toList(),
                queries.salesBySpecies(start, end).stream().map(group).toList());
    }

    /** Restocks, sales and losses from the stock ledger, with the loss rate. */
    public StockStatsView stock(LocalDate from, LocalDate to) {
        StatsPeriod period = StatsPeriod.of(from, to);
        StatsPeriod previous = period.previous();
        Map<StockMovementReason, Long> now = byReason(queries.stockByReason(period.start(ZONE), period.end(ZONE)));
        Map<StockMovementReason, Long> before =
                byReason(queries.stockByReason(previous.start(ZONE), previous.end(ZONE)));

        long sold = sold(now);
        long soldBefore = sold(before);
        long lost = -now.getOrDefault(StockMovementReason.LOSS, 0L);
        long lostBefore = -before.getOrDefault(StockMovementReason.LOSS, 0L);
        List<StockReasonView> reasons = now.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .map(entry -> new StockReasonView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong((StockReasonView view) -> Math.abs(view.units())).reversed()
                        .thenComparing(StockReasonView::reason))
                .toList();
        return new StockStatsView(
                periodView(period),
                count(now.getOrDefault(StockMovementReason.RESTOCK, 0L),
                        before.getOrDefault(StockMovementReason.RESTOCK, 0L)),
                count(sold, soldBefore),
                count(lost, lostBefore),
                new ShareKpiView(shareBps(lost, sold + lost), shareBps(lostBefore, soldBefore + lostBefore)),
                reasons);
    }

    /**
     * Daily sales as CSV for bookkeeping: one row per day of the period (days without sales
     * included), amounts in euros with a decimal point. Contains no personal data.
     */
    public String salesCsv(LocalDate from, LocalDate to) {
        StatsPeriod period = StatsPeriod.of(from, to);
        Map<LocalDate, DayRow> days = queries.salesByDay(period.start(ZONE), period.end(ZONE), ZONE).stream()
                .collect(Collectors.toMap(DayRow::day, Function.identity()));
        StringBuilder csv = new StringBuilder(
                "date,orders,paid_orders,revenue_gross_eur,vat_eur,shipping_eur,revenue_net_eur,animals\r\n");
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            DayRow row = days.getOrDefault(day, new DayRow(day, 0, 0, 0, 0, 0, 0));
            csv.append(day).append(',')
                    .append(row.orders()).append(',')
                    .append(row.paidOrders()).append(',')
                    .append(euros(row.revenueCents())).append(',')
                    .append(euros(row.taxCents())).append(',')
                    .append(euros(row.shippingCents())).append(',')
                    .append(euros(row.revenueCents() - row.taxCents())).append(',')
                    .append(row.animals()).append("\r\n");
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------ helpers

    private List<SalesBucketView> series(StatsPeriod period, Instant start, Instant end) {
        Map<LocalDate, BucketRow> paid = new HashMap<>();
        Map<LocalDate, BucketRow> awaiting = new HashMap<>();
        for (BucketRow row : queries.salesByBucket(start, end, period.granularity().sqlUnit(), ZONE)) {
            (row.paid() ? paid : awaiting).put(row.bucket(), row);
        }
        BucketRow none = new BucketRow(null, false, 0, 0, 0);
        return period.buckets().stream()
                .map(bucket -> {
                    BucketRow p = paid.getOrDefault(bucket, none);
                    BucketRow a = awaiting.getOrDefault(bucket, none);
                    return new SalesBucketView(bucket, Money.eur(p.revenueCents() + a.revenueCents()),
                            p.orders() + a.orders(), p.animals() + a.animals(), Money.eur(p.revenueCents()),
                            Money.eur(a.revenueCents()), p.orders(), a.orders(), p.animals(), a.animals());
                })
                .toList();
    }

    private static Map<StockMovementReason, Long> byReason(List<ReasonRow> rows) {
        Map<StockMovementReason, Long> map = new EnumMap<>(StockMovementReason.class);
        rows.forEach(row -> map.put(StockMovementReason.valueOf(row.reason()), row.units()));
        return map;
    }

    /** Animals that left stock with orders, net of those returned by cancellations. */
    private static long sold(Map<StockMovementReason, Long> byReason) {
        return -(byReason.getOrDefault(StockMovementReason.ORDER_PLACED, 0L)
                + byReason.getOrDefault(StockMovementReason.ORDER_CANCELLED, 0L));
    }

    private static StatsPeriodView periodView(StatsPeriod period) {
        StatsPeriod previous = period.previous();
        return new StatsPeriodView(period.from(), period.to(), period.granularity(), previous.from(), previous.to());
    }

    private static CountKpiView count(long value, long previous) {
        return new CountKpiView(value, previous, changeBps(value, previous));
    }

    private static MoneyKpiView money(long cents, long previousCents) {
        return new MoneyKpiView(Money.eur(cents), Money.eur(previousCents), changeBps(cents, previousCents));
    }

    private static String euros(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
