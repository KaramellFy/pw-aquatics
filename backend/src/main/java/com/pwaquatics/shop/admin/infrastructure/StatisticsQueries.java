package com.pwaquatics.shop.admin.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only SQL for the dashboard and sales statistics (docs/DATABASE.md §4, "Statistics").
 *
 * <p>This is a read model: it aggregates across the order, catalog, inventory and identity
 * tables in the database instead of loading entities, and it never writes. Which orders count
 * as sales is defined once, in {@link #COUNTED}; amounts are gross (incl. VAT) in cents.
 */
@Repository
public class StatisticsQueries {

    /** Orders that count as sales: not cancelled (which includes expired) and not refunded. */
    static final String COUNTED = "o.status <> 'CANCELLED' AND o.payment_status <> 'REFUNDED'";

    /** Orders whose money has arrived. */
    static final String PAID = "o.payment_status IN ('PAID', 'PARTIALLY_REFUNDED')";

    /** Live animals in an order (packs × animals per pack). */
    private static final String ANIMALS = """
            coalesce((SELECT sum(i.quantity * i.pack_size) FROM order_items i
                      WHERE i.order_id = o.id AND i.is_live_animal), 0)""";

    private final JdbcClient jdbc;

    StatisticsQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ rows

    /** Headline figures of one period. */
    public record SalesTotals(
            long placedOrders,
            long orders,
            long revenueCents,
            long paidRevenueCents,
            long cancelledOrders,
            long returningOrders,
            long animals) {}

    /** One time bucket, split into paid and not yet paid orders. */
    public record BucketRow(LocalDate bucket, boolean paid, long orders, long revenueCents, long animals) {}

    /** One day of the CSV export. */
    public record DayRow(
            LocalDate day,
            long orders,
            long paidOrders,
            long revenueCents,
            long taxCents,
            long shippingCents,
            long animals) {}

    public record StatusRow(String status, long orders, long totalCents) {}

    public record WeekdayRow(int isoWeekday, long orders, long revenueCents) {}

    public record CountryRow(String countryCode, long orders, long revenueCents) {}

    public record ProductRow(
            UUID productId, String name, String gradeName, long units, long packs, long revenueCents) {}

    public record GroupRow(UUID id, String name, long units, long revenueCents) {}

    public record ReasonRow(String reason, long units) {}

    public record OpenOrders(long awaitingPayment, long awaitingPaymentCents, long toShip) {}

    public record ProductCounts(long live, long lowStock, long soldOut) {}

    public record RecentOrderRow(
            String orderNumber,
            Instant placedAt,
            String status,
            String paymentStatus,
            long totalCents,
            String firstName,
            String lastName,
            String countryCode,
            long animals) {}

    public record LowStockRow(UUID id, String name, int stockUnits, int lowStockThreshold) {}

    // ------------------------------------------------------------------ sales

    public SalesTotals salesTotals(Instant start, Instant end) {
        return jdbc.sql("""
                        WITH o AS (
                            SELECT o.status, o.total_cents,
                                   (%s) AS counted,
                                   (%s) AS paid,
                                   %s AS animals,
                                   EXISTS (SELECT 1 FROM orders e
                                           WHERE e.customer_email = o.customer_email
                                             AND e.placed_at < o.placed_at
                                             AND e.status <> 'CANCELLED') AS is_returning
                            FROM orders o
                            WHERE o.placed_at >= :start AND o.placed_at < :end)
                        SELECT count(*)                                                   AS placed,
                               count(*) FILTER (WHERE counted)                             AS orders,
                               coalesce(sum(total_cents) FILTER (WHERE counted), 0)        AS revenue,
                               coalesce(sum(total_cents) FILTER (WHERE counted AND paid), 0) AS paid_revenue,
                               count(*) FILTER (WHERE status = 'CANCELLED')                AS cancelled,
                               count(*) FILTER (WHERE counted AND is_returning)             AS returning_orders,
                               coalesce(sum(animals) FILTER (WHERE counted), 0)            AS animals
                        FROM o
                        """.formatted(COUNTED, PAID, ANIMALS))
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new SalesTotals(rs.getLong("placed"), rs.getLong("orders"), rs.getLong("revenue"),
                        rs.getLong("paid_revenue"), rs.getLong("cancelled"), rs.getLong("returning_orders"),
                        rs.getLong("animals")))
                .single();
    }

    public long newAccounts(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT count(*) FROM users
                        WHERE role = 'CUSTOMER' AND created_at >= :start AND created_at < :end
                        """)
                .param("start", utc(start))
                .param("end", utc(end))
                .query(Long.class)
                .single();
    }

    /** Counted orders per bucket ({@code unit}: day, week or month) and paid/unpaid. */
    public List<BucketRow> salesByBucket(Instant start, Instant end, String unit, ZoneId zone) {
        return jdbc.sql("""
                        SELECT date_trunc(:unit, o.placed_at AT TIME ZONE :zone)::date AS bucket,
                               (%s)                       AS paid,
                               count(*)                   AS orders,
                               sum(o.total_cents)::bigint AS revenue,
                               sum(%s)::bigint            AS animals
                        FROM orders o
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY 1, 2
                        ORDER BY 1
                        """.formatted(PAID, ANIMALS, COUNTED))
                .param("unit", unit)
                .param("zone", zone.getId())
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new BucketRow(rs.getObject("bucket", LocalDate.class), rs.getBoolean("paid"),
                        rs.getLong("orders"), rs.getLong("revenue"), rs.getLong("animals")))
                .list();
    }

    /** Counted orders per calendar day, for the CSV export. Days without orders are absent. */
    public List<DayRow> salesByDay(Instant start, Instant end, ZoneId zone) {
        return jdbc.sql("""
                        SELECT (o.placed_at AT TIME ZONE :zone)::date AS day,
                               count(*)                               AS orders,
                               count(*) FILTER (WHERE %s)             AS paid_orders,
                               sum(o.total_cents)::bigint             AS revenue,
                               sum(o.tax_cents)::bigint               AS tax,
                               sum(o.shipping_cents)::bigint          AS shipping,
                               sum(%s)::bigint                        AS animals
                        FROM orders o
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY 1
                        ORDER BY 1
                        """.formatted(PAID, ANIMALS, COUNTED))
                .param("zone", zone.getId())
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new DayRow(rs.getObject("day", LocalDate.class), rs.getLong("orders"),
                        rs.getLong("paid_orders"), rs.getLong("revenue"), rs.getLong("tax"), rs.getLong("shipping"),
                        rs.getLong("animals")))
                .list();
    }

    /** All orders placed in the period by their current status (cancelled ones included). */
    public List<StatusRow> ordersByStatus(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT o.status, count(*) AS orders, coalesce(sum(o.total_cents), 0)::bigint AS total
                        FROM orders o
                        WHERE o.placed_at >= :start AND o.placed_at < :end
                        GROUP BY o.status
                        """)
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new StatusRow(rs.getString("status"), rs.getLong("orders"), rs.getLong("total")))
                .list();
    }

    public List<WeekdayRow> salesByWeekday(Instant start, Instant end, ZoneId zone) {
        return jdbc.sql("""
                        SELECT extract(isodow FROM o.placed_at AT TIME ZONE :zone)::int AS weekday,
                               count(*) AS orders, sum(o.total_cents)::bigint AS revenue
                        FROM orders o
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY 1
                        """.formatted(COUNTED))
                .param("zone", zone.getId())
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new WeekdayRow(rs.getInt("weekday"), rs.getLong("orders"), rs.getLong("revenue")))
                .list();
    }

    /** Counted orders by the country they are shipped to, highest revenue first. */
    public List<CountryRow> salesByCountry(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT a.country_code, count(*) AS orders, sum(o.total_cents)::bigint AS revenue
                        FROM orders o
                        JOIN order_addresses a ON a.order_id = o.id AND a.type = 'SHIPPING'
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY a.country_code
                        ORDER BY revenue DESC, a.country_code
                        """.formatted(COUNTED))
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new CountryRow(rs.getString("country_code").trim(), rs.getLong("orders"),
                        rs.getLong("revenue")))
                .list();
    }

    // ------------------------------------------------------------------ products

    /**
     * Best sellers by revenue (line totals: goods only, without shipping). The current
     * product name and grade are shown; those at the time of sale are the fallback.
     */
    public List<ProductRow> topProducts(Instant start, Instant end, int limit) {
        return jdbc.sql("""
                        SELECT i.product_id,
                               coalesce(max(p.name), max(i.product_name))    AS name,
                               coalesce(max(g.name), max(i.grade_name))      AS grade,
                               sum(i.quantity * i.pack_size)::bigint          AS units,
                               sum(i.quantity)::bigint                        AS packs,
                               sum(i.line_total_cents)::bigint                AS revenue
                        FROM order_items i
                        JOIN orders o ON o.id = i.order_id
                        LEFT JOIN products p ON p.id = i.product_id
                        LEFT JOIN grades g ON g.id = p.grade_id
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY i.product_id
                        ORDER BY revenue DESC, name
                        LIMIT :limit
                        """.formatted(COUNTED))
                .param("start", utc(start))
                .param("end", utc(end))
                .param("limit", limit)
                .query((rs, n) -> new ProductRow(rs.getObject("product_id", UUID.class), rs.getString("name"),
                        rs.getString("grade"), rs.getLong("units"), rs.getLong("packs"), rs.getLong("revenue")))
                .list();
    }

    /** Revenue per product category (the product's current category). */
    public List<GroupRow> salesByCategory(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT c.id, c.name,
                               sum(i.quantity * i.pack_size)::bigint AS units,
                               sum(i.line_total_cents)::bigint       AS revenue
                        FROM order_items i
                        JOIN orders o ON o.id = i.order_id
                        LEFT JOIN products p ON p.id = i.product_id
                        LEFT JOIN categories c ON c.id = p.category_id
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY c.id, c.name
                        ORDER BY revenue DESC, c.name
                        """.formatted(COUNTED))
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new GroupRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("units"), rs.getLong("revenue")))
                .list();
    }

    /** Revenue per species (the species name stored with the order line). */
    public List<GroupRow> salesBySpecies(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT i.species_name AS name,
                               sum(i.quantity * i.pack_size)::bigint AS units,
                               sum(i.line_total_cents)::bigint       AS revenue
                        FROM order_items i
                        JOIN orders o ON o.id = i.order_id
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND %s
                        GROUP BY i.species_name
                        ORDER BY revenue DESC, i.species_name
                        """.formatted(COUNTED))
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new GroupRow(null, rs.getString("name"), rs.getLong("units"),
                        rs.getLong("revenue")))
                .list();
    }

    // ------------------------------------------------------------------ stock

    /** Net change in animals per ledger reason. */
    public List<ReasonRow> stockByReason(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT reason, sum(delta)::bigint AS units
                        FROM stock_movements
                        WHERE created_at >= :start AND created_at < :end
                        GROUP BY reason
                        """)
                .param("start", utc(start))
                .param("end", utc(end))
                .query((rs, n) -> new ReasonRow(rs.getString("reason"), rs.getLong("units")))
                .list();
    }

    // ------------------------------------------------------------------ right now

    public OpenOrders openOrders() {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE status = 'PENDING_PAYMENT')                       AS awaiting,
                               coalesce(sum(total_cents) FILTER (WHERE status = 'PENDING_PAYMENT'), 0)  AS awaiting_cents,
                               count(*) FILTER (WHERE status IN ('PAID', 'PROCESSING'))                AS to_ship
                        FROM orders
                        WHERE status IN ('PENDING_PAYMENT', 'PAID', 'PROCESSING')
                        """)
                .query((rs, n) -> new OpenOrders(rs.getLong("awaiting"), rs.getLong("awaiting_cents"),
                        rs.getLong("to_ship")))
                .single();
    }

    public ProductCounts productCounts() {
        return jdbc.sql("""
                        SELECT count(*)                                                              AS live,
                               count(*) FILTER (WHERE stock_units > 0
                                                  AND stock_units <= low_stock_threshold)            AS low,
                               count(*) FILTER (WHERE stock_units = 0)                               AS sold_out
                        FROM products
                        WHERE status = 'ACTIVE'
                        """)
                .query((rs, n) -> new ProductCounts(rs.getLong("live"), rs.getLong("low"), rs.getLong("sold_out")))
                .single();
    }

    public List<RecentOrderRow> recentOrders(int limit) {
        return jdbc.sql("""
                        SELECT o.order_number, o.placed_at, o.status, o.payment_status, o.total_cents,
                               a.first_name, a.last_name, a.country_code, %s AS animals
                        FROM orders o
                        LEFT JOIN order_addresses a ON a.order_id = o.id AND a.type = 'SHIPPING'
                        ORDER BY o.placed_at DESC
                        LIMIT :limit
                        """.formatted(ANIMALS))
                .param("limit", limit)
                .query((rs, n) -> new RecentOrderRow(rs.getString("order_number"),
                        rs.getObject("placed_at", OffsetDateTime.class).toInstant(), rs.getString("status"),
                        rs.getString("payment_status"), rs.getLong("total_cents"), rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("country_code") == null ? null : rs.getString("country_code").trim(),
                        rs.getLong("animals")))
                .list();
    }

    /** Published products at or below their low-stock threshold, emptiest first. */
    public List<LowStockRow> lowStockProducts(int limit) {
        return jdbc.sql("""
                        SELECT id, name, stock_units, low_stock_threshold
                        FROM products
                        WHERE status = 'ACTIVE' AND stock_units <= low_stock_threshold
                        ORDER BY stock_units, name
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new LowStockRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getInt("stock_units"), rs.getInt("low_stock_threshold")))
                .list();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
