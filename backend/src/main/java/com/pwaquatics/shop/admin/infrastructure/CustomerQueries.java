package com.pwaquatics.shop.admin.infrastructure;

import static com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.COUNTED;
import static com.pwaquatics.shop.admin.infrastructure.StatisticsQueries.PAID;

import com.pwaquatics.shop.admin.application.view.CustomerDetailView;
import com.pwaquatics.shop.admin.application.view.CustomerSummaryView;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.identity.domain.UserStatus;
import com.pwaquatics.shop.order.domain.OrderStatus;
import com.pwaquatics.shop.order.domain.PaymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read model for the admin customer pages: accounts with their orders and what they have spent.
 * Like {@link StatisticsQueries} it reads across the identity, customer and order tables and
 * never writes. Deleted (anonymised) accounts and administrators are left out.
 */
@Repository
public class CustomerQueries {

    /** Per customer: all orders, paid orders that count as sales, their sum and average, last order. */
    private static final String ORDER_FIGURES = """
            LEFT JOIN LATERAL (
                SELECT count(*) AS order_count,
                       count(*) FILTER (WHERE %1$s AND %2$s) AS paid_count,
                       coalesce(sum(o.total_cents) FILTER (WHERE %1$s AND %2$s), 0) AS lifetime_cents,
                       round(avg(o.total_cents) FILTER (WHERE %1$s AND %2$s)) AS average_cents,
                       max(o.placed_at) AS last_order_at
                FROM orders o WHERE o.user_id = u.id
            ) f ON true
            """.formatted(COUNTED, PAID);

    private static final String CUSTOMERS = """
            FROM users u
            WHERE u.role = 'CUSTOMER' AND u.status <> 'ANONYMISED'
              AND (:anyStatus OR u.status = :status)
              AND (:text = ''
                   OR lower(u.email::text) LIKE :pattern ESCAPE '\\'
                   OR lower(u.first_name || ' ' || u.last_name) LIKE :pattern ESCAPE '\\')
            """;

    private final JdbcClient jdbc;

    CustomerQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Customers, newest first.
     *
     * @param text part of the email address or name, or null
     * @param status only accounts with this status, or null for active and locked ones
     */
    public Page<CustomerSummaryView> search(String text, UserStatus status, PageRequest page) {
        String normalised = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        String pattern = "%" + normalised.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        long total = jdbc.sql("SELECT count(*) " + CUSTOMERS)
                .param("anyStatus", status == null)
                .param("status", status == null ? "NONE" : status.name())
                .param("text", normalised)
                .param("pattern", pattern)
                .query(Long.class)
                .single();
        List<CustomerSummaryView> rows = jdbc.sql("""
                        SELECT u.id, u.first_name, u.last_name, u.email::text AS email, u.status,
                               u.email_verified_at IS NOT NULL AS verified, u.created_at, u.last_login_at,
                               f.order_count, f.lifetime_cents, f.last_order_at
                        FROM (SELECT u.*
                        """ + CUSTOMERS + """
                              ORDER BY u.created_at DESC, u.id DESC LIMIT :limit OFFSET :offset) u
                        """ + ORDER_FIGURES + " ORDER BY u.created_at DESC, u.id DESC")
                .param("anyStatus", status == null)
                .param("status", status == null ? "NONE" : status.name())
                .param("text", normalised)
                .param("pattern", pattern)
                .param("limit", page.getPageSize())
                .param("offset", page.getOffset())
                .query(CustomerQueries::summary)
                .list();
        return new PageImpl<>(rows, page, total);
    }

    /** One customer with addresses and their latest 50 orders; empty for unknown, deleted or admin accounts. */
    public Optional<CustomerDetailView> find(UUID userId) {
        List<CustomerDetailView.CustomerAddress> addresses = jdbc.sql("""
                        SELECT id, label, first_name, last_name, company, street, house_number, address_line2,
                               postal_code, city, country_code, phone, is_default_shipping, is_default_billing
                        FROM addresses WHERE user_id = :id ORDER BY is_default_shipping DESC, created_at, id
                        """)
                .param("id", userId)
                .query(CustomerQueries::address)
                .list();
        List<CustomerDetailView.CustomerOrder> orders = jdbc.sql("""
                        SELECT o.order_number, o.placed_at, o.status, o.payment_status, o.total_cents, o.currency,
                               (SELECT coalesce(sum(i.quantity), 0) FROM order_items i WHERE i.order_id = o.id) AS item_count
                        FROM orders o WHERE o.user_id = :id ORDER BY o.placed_at DESC, o.id DESC LIMIT 50
                        """)
                .param("id", userId)
                .query(CustomerQueries::order)
                .list();
        return jdbc.sql("""
                        SELECT u.id, u.first_name, u.last_name, u.email::text AS email, u.phone, u.locale, u.status,
                               u.email_verified_at IS NOT NULL AS verified, u.marketing_opt_in, u.created_at,
                               u.last_login_at, f.order_count, f.paid_count, f.lifetime_cents, f.average_cents
                        FROM users u
                        """ + ORDER_FIGURES + """
                        WHERE u.id = :id AND u.role = 'CUSTOMER' AND u.status <> 'ANONYMISED'
                        """)
                .param("id", userId)
                .query((rs, n) -> detail(rs, addresses, orders))
                .optional();
    }

    private static CustomerSummaryView summary(ResultSet rs, int rowNumber) throws SQLException {
        return new CustomerSummaryView(
                rs.getObject("id", UUID.class),
                rs.getString("first_name") + " " + rs.getString("last_name"),
                rs.getString("email"),
                UserStatus.valueOf(rs.getString("status")),
                rs.getBoolean("verified"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("last_login_at")),
                rs.getInt("order_count"),
                Money.eur(rs.getLong("lifetime_cents")),
                instant(rs.getTimestamp("last_order_at")));
    }

    private static CustomerDetailView detail(
            ResultSet rs, List<CustomerDetailView.CustomerAddress> addresses, List<CustomerDetailView.CustomerOrder> orders)
            throws SQLException {
        long average = rs.getLong("average_cents");
        boolean hasAverage = !rs.wasNull();
        UserStatus status = UserStatus.valueOf(rs.getString("status"));
        return new CustomerDetailView(
                rs.getObject("id", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("phone"),
                rs.getString("locale"),
                status,
                status == UserStatus.ACTIVE || status == UserStatus.LOCKED,
                rs.getBoolean("verified"),
                rs.getBoolean("marketing_opt_in"),
                rs.getTimestamp("created_at").toInstant(),
                instant(rs.getTimestamp("last_login_at")),
                rs.getInt("order_count"),
                rs.getInt("paid_count"),
                Money.eur(rs.getLong("lifetime_cents")),
                hasAverage ? Money.eur(average) : null,
                addresses,
                orders);
    }

    private static CustomerDetailView.CustomerAddress address(ResultSet rs, int rowNumber) throws SQLException {
        return new CustomerDetailView.CustomerAddress(
                rs.getObject("id", UUID.class),
                rs.getString("label"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("company"),
                rs.getString("street"),
                rs.getString("house_number"),
                rs.getString("address_line2"),
                rs.getString("postal_code"),
                rs.getString("city"),
                rs.getString("country_code"),
                rs.getString("phone"),
                rs.getBoolean("is_default_shipping"),
                rs.getBoolean("is_default_billing"));
    }

    private static CustomerDetailView.CustomerOrder order(ResultSet rs, int rowNumber) throws SQLException {
        return new CustomerDetailView.CustomerOrder(
                rs.getString("order_number"),
                rs.getTimestamp("placed_at").toInstant(),
                OrderStatus.valueOf(rs.getString("status")),
                PaymentStatus.valueOf(rs.getString("payment_status")),
                new Money(rs.getLong("total_cents"), rs.getString("currency")),
                rs.getInt("item_count"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
