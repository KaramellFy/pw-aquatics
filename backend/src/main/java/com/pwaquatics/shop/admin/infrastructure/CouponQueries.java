package com.pwaquatics.shop.admin.infrastructure;

import static com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.COUNTED;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read model for coupon figures: how often a coupon was used, what it saved customers and the
 * revenue of those orders. Reads the coupon and order tables and never writes. Only orders that
 * count as sales ({@link StatisticsQueries#COUNTED}) are counted; amounts are gross cents.
 */
@Repository
public class CouponQueries {

    private final JdbcClient jdbc;

    CouponQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** All-time figures of one coupon. */
    public record Usage(long orders, long savingCents, long revenueCents) {}

    /** One order that used a coupon. */
    public record RedemptionRow(
            String orderNumber,
            Instant placedAt,
            String customerName,
            String orderStatus,
            long orderTotalCents,
            long savingCents,
            boolean released) {}

    /** One coupon's figures in a period. */
    public record CouponRow(String code, long orders, long savingCents, long revenueCents) {}

    public Usage usage(UUID couponId) {
        return jdbc.sql("""
                        SELECT count(*) AS orders, coalesce(sum(r.discount_cents), 0) AS saving,
                               coalesce(sum(o.total_cents), 0) AS revenue
                        FROM coupon_redemptions r JOIN orders o ON o.id = r.order_id
                        WHERE r.coupon_id = :id AND
                        """ + COUNTED)
                .param("id", couponId)
                .query((rs, n) -> new Usage(rs.getLong("orders"), rs.getLong("saving"), rs.getLong("revenue")))
                .single();
    }

    public long redemptionCount(UUID couponId) {
        return jdbc.sql("SELECT count(*) FROM coupon_redemptions WHERE coupon_id = :id")
                .param("id", couponId)
                .query(Long.class)
                .single();
    }

    /** Orders that used the coupon, newest first, including cancelled ones (marked as released). */
    public List<RedemptionRow> redemptions(UUID couponId, int limit, long offset) {
        return jdbc.sql("""
                        SELECT o.order_number, o.placed_at, o.status, o.total_cents, r.discount_cents,
                               r.released_at IS NOT NULL AS released,
                               trim(coalesce(a.first_name, '') || ' ' || coalesce(a.last_name, '')) AS customer_name
                        FROM coupon_redemptions r
                        JOIN orders o ON o.id = r.order_id
                        LEFT JOIN order_addresses a ON a.order_id = o.id AND a.type = 'BILLING'
                        WHERE r.coupon_id = :id
                        ORDER BY o.placed_at DESC, o.id DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("id", couponId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> new RedemptionRow(
                        rs.getString("order_number"),
                        rs.getObject("placed_at", OffsetDateTime.class).toInstant(),
                        rs.getString("customer_name"),
                        rs.getString("status"),
                        rs.getLong("total_cents"),
                        rs.getLong("discount_cents"),
                        rs.getBoolean("released")))
                .list();
    }

    /** Per coupon code: orders placed in the period that used it, what it saved, their revenue. */
    public List<CouponRow> byCoupon(Instant start, Instant end) {
        return jdbc.sql("""
                        SELECT o.coupon_code AS code, count(*) AS orders, sum(r.discount_cents) AS saving,
                               sum(o.total_cents) AS revenue
                        FROM coupon_redemptions r JOIN orders o ON o.id = r.order_id
                        WHERE o.placed_at >= :start AND o.placed_at < :end AND
                        """ + COUNTED + """

                        GROUP BY o.coupon_code
                        ORDER BY revenue DESC, code
                        """)
                .param("start", start.atOffset(ZoneOffset.UTC))
                .param("end", end.atOffset(ZoneOffset.UTC))
                .query((rs, n) -> new CouponRow(
                        rs.getString("code"), rs.getLong("orders"), rs.getLong("saving"), rs.getLong("revenue")))
                .list();
    }
}
