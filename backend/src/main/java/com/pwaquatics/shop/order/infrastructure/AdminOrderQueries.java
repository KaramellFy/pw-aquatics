package com.pwaquatics.shop.order.infrastructure;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.order.application.view.AdminOrderSummaryView;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import com.pwaquatics.shop.payment.domain.PaymentMethod;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read-only search over orders for the admin order list (one SQL query per page). */
@Repository
public class AdminOrderQueries {

    private static final String FROM_WHERE = """
            FROM orders o
            JOIN order_addresses b ON b.order_id = o.id AND b.type = 'BILLING'
            JOIN order_addresses s ON s.order_id = o.id AND s.type = 'SHIPPING'
            WHERE (:anyStatus OR o.status IN (:statuses))
              AND (:anyPayment OR o.payment_status = :paymentStatus)
              AND (CAST(:placedFrom AS timestamptz) IS NULL OR o.placed_at >= CAST(:placedFrom AS timestamptz))
              AND (CAST(:placedBefore AS timestamptz) IS NULL OR o.placed_at < CAST(:placedBefore AS timestamptz))
              AND (:text = ''
                   OR lower(o.order_number) LIKE :pattern ESCAPE '\\'
                   OR lower(o.customer_email::text) LIKE :pattern ESCAPE '\\'
                   OR lower(b.first_name || ' ' || b.last_name) LIKE :pattern ESCAPE '\\'
                   OR lower(s.first_name || ' ' || s.last_name) LIKE :pattern ESCAPE '\\')
            """;

    private final JdbcClient jdbc;

    AdminOrderQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param statuses any of these; empty for all
     * @param placedFrom inclusive, or null
     * @param placedBefore exclusive, or null
     */
    public Page<AdminOrderSummaryView> search(
            String text,
            Collection<OrderStatus> statuses,
            PaymentStatus paymentStatus,
            Instant placedFrom,
            Instant placedBefore,
            boolean oldestFirst,
            PageRequest page) {
        String normalised = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + normalised.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<String> statusNames = statuses.isEmpty()
                ? List.of("NONE")
                : statuses.stream().map(Enum::name).toList();
        String order = oldestFirst ? "o.placed_at ASC, o.id ASC" : "o.placed_at DESC, o.id DESC";

        long total = bind(jdbc.sql("SELECT count(*) " + FROM_WHERE), normalised, pattern, statuses, statusNames,
                        paymentStatus, placedFrom, placedBefore)
                .query(Long.class)
                .single();
        List<AdminOrderSummaryView> rows = bind(jdbc.sql("""
                        SELECT o.order_number, o.placed_at, o.status, o.payment_status, o.payment_method,
                               o.total_cents, o.currency, b.first_name, b.last_name, o.customer_email,
                               s.country_code, o.planned_dispatch_date, o.payment_due_at,
                               (SELECT coalesce(sum(i.quantity), 0) FROM order_items i WHERE i.order_id = o.id) AS item_count,
                               EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id AND i.is_live_animal) AS live
                        """ + FROM_WHERE + " ORDER BY " + order + " LIMIT :limit OFFSET :offset"),
                        normalised, pattern, statuses, statusNames, paymentStatus, placedFrom, placedBefore)
                .param("limit", page.getPageSize())
                .param("offset", page.getOffset())
                .query(AdminOrderQueries::row)
                .list();
        return new PageImpl<>(rows, page, total);
    }

    private static JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec spec,
            String text,
            String pattern,
            Collection<OrderStatus> statuses,
            List<String> statusNames,
            PaymentStatus paymentStatus,
            Instant placedFrom,
            Instant placedBefore) {
        return spec.param("anyStatus", statuses.isEmpty())
                .param("statuses", statusNames)
                .param("anyPayment", paymentStatus == null)
                .param("paymentStatus", paymentStatus == null ? "NONE" : paymentStatus.name())
                .param("placedFrom", placedFrom == null ? null : Timestamp.from(placedFrom))
                .param("placedBefore", placedBefore == null ? null : Timestamp.from(placedBefore))
                .param("text", text)
                .param("pattern", pattern);
    }

    private static AdminOrderSummaryView row(ResultSet rs, int rowNumber) throws SQLException {
        Date dispatch = rs.getDate("planned_dispatch_date");
        Timestamp due = rs.getTimestamp("payment_due_at");
        return new AdminOrderSummaryView(
                rs.getString("order_number"),
                rs.getTimestamp("placed_at").toInstant(),
                OrderStatus.valueOf(rs.getString("status")),
                PaymentStatus.valueOf(rs.getString("payment_status")),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                new Money(rs.getLong("total_cents"), rs.getString("currency")),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("customer_email"),
                rs.getString("country_code"),
                rs.getInt("item_count"),
                rs.getBoolean("live"),
                dispatch == null ? null : dispatch.toLocalDate(),
                due == null ? null : due.toInstant());
    }
}
