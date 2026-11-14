package com.pwaquatics.shop.order;

import static com.pwaquatics.shop.support.AuthFixture.json;
import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static com.pwaquatics.shop.support.TestRequests.uniqueClientIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pwaquatics.shop.customer.AddressApiTest;
import com.pwaquatics.shop.notification.domain.OutboxMessage;
import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Order handling by the shop owner (docs/API.md §3.5): an order goes placed → paid → being
 * packed → shipped → delivered through the admin, and the customer sees every step.
 */
@IntegrationTest
@Sql(scripts = "/order/order-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/order/order-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminOrderApiTest {

    private static final String ALPHA_SINGLE = "22222222-0000-0000-0000-000000000201"; // 3.00, live
    private static final String ALPHA_PRODUCT = "22222222-0000-0000-0000-000000000101";
    private static final String LIVE = "TEST_LIVE"; // 9.90
    private static final long ONE_ANIMAL_TOTAL = 1290;
    private static final String ADMIN_ORDERS = "/api/v1/admin/orders";
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private String adminEmail;
    private Cookie admin;

    @BeforeEach
    void signInAdmin() throws Exception {
        adminEmail = uniqueEmail();
        admin = auth.adminSession(adminEmail);
    }

    // ------------------------------------------------------------------ access

    @Test
    void onlyAdministratorsCanSeeOrChangeOrders() throws Exception {
        Customer customer = customer();
        String orderNumber = placeOrder(customer, "BANK_TRANSFER");

        mvc.perform(get(ADMIN_ORDERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_ORDERS + "/" + orderNumber)).andExpect(status().isUnauthorized());
        mvc.perform(json(post(ADMIN_ORDERS + "/" + orderNumber + "/mark-paid"), "{}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get(ADMIN_ORDERS).cookie(customer.session())).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_ORDERS + "/" + orderNumber).cookie(customer.session()))
                .andExpect(status().isForbidden());
        for (String action : List.of("/mark-paid", "/status", "/cancel", "/notes", "/shipments")) {
            mvc.perform(json(post(ADMIN_ORDERS + "/" + orderNumber + action), "{}").cookie(customer.session()))
                    .andExpect(status().isForbidden());
        }
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING_PAYMENT");

        // Changes need the CSRF token, also for administrators.
        mvc.perform(post(ADMIN_ORDERS + "/" + orderNumber + "/mark-paid").cookie(admin)
                        .contentType("application/json").content("{}").with(uniqueClientIp()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PENDING_PAYMENT");
    }

    // ------------------------------------------------------------------ the whole way

    @Test
    void anOrderGoesFromPlacedToDeliveredAndTheCustomerSeesEveryStep() throws Exception {
        Customer customer = customer();
        String orderNumber = placeOrder(customer, "BANK_TRANSFER");

        mvc.perform(get(ADMIN_ORDERS + "/" + orderNumber).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.customerEmail").value(customer.email()))
                .andExpect(jsonPath("$.payment").value(nullValue()))
                .andExpect(jsonPath("$.carriers").value(contains("DHL", "DHL Express")))
                .andExpect(jsonPath("$.actions.markPaid").value(true))
                .andExpect(jsonPath("$.actions.ship").value(false))
                .andExpect(jsonPath("$.actions.cancel").value(true));

        // 1. The bank transfer arrives.
        action(orderNumber, "/mark-paid", "{\"note\":\"Found on the account\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("PAID"))
                .andExpect(jsonPath("$.order.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paidAt").value(notNullValue()))
                .andExpect(jsonPath("$.history[1].to").value("PAID"))
                .andExpect(jsonPath("$.history[1].note").value("Found on the account"))
                .andExpect(jsonPath("$.history[1].changedBy").value("Anna Schmidt"))
                .andExpect(jsonPath("$.actions.markPaid").value(false))
                .andExpect(jsonPath("$.actions.startProcessing").value(true))
                .andExpect(jsonPath("$.actions.ship").value(true));
        assertThat(lastEmail(customer).getPayload().subject()).isEqualTo("Payment received – order " + orderNumber);
        customerView(customer, orderNumber)
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.shipments", hasSize(0)));

        // 2. Packing starts.
        action(orderNumber, "/status", """
                        {"status":"PROCESSING","customerMessage":"Your shrimp are getting a heat pack."}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("PROCESSING"));
        OutboxMessage processing = lastEmail(customer);
        assertThat(processing.getPayload().subject()).isEqualTo("We are preparing your order " + orderNumber);
        assertThat(processing.getPayload().body()).contains("Your shrimp are getting a heat pack.");

        // 3. The parcel leaves; spaces in the tracking number are dropped.
        MvcResult shipped = action(orderNumber, "/shipments", """
                        {"carrier":"DHL","trackingNumber":"JJD 0001 2345 6789","customerMessage":"Please unpack it straight away."}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("SHIPPED"))
                .andExpect(jsonPath("$.order.shipments", hasSize(1)))
                .andExpect(jsonPath("$.order.shipments[0].carrier").value("DHL"))
                .andExpect(jsonPath("$.order.shipments[0].trackingNumber").value("JJD000123456789"))
                .andExpect(jsonPath("$.order.shipments[0].trackingUrl")
                        .value(endsWith("tracking-parcel.html?submit=1&tracking-id=JJD000123456789")))
                .andExpect(jsonPath("$.actions.markDelivered").value(true))
                .andExpect(jsonPath("$.actions.cancel").value(false))
                .andReturn();
        String trackingUrl = read(shipped, "$.order.shipments[0].trackingUrl");
        OutboxMessage onItsWay = lastEmail(customer);
        assertThat(onItsWay.getPayload().subject()).isEqualTo("Your order " + orderNumber + " is on its way");
        assertThat(onItsWay.getPayload().body())
                .contains("JJD000123456789", trackingUrl, "live animals", "Please unpack it straight away.");
        customerView(customer, orderNumber)
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipments[0].trackingNumber").value("JJD000123456789"))
                .andExpect(jsonPath("$.shipments[0].trackingUrl").value(trackingUrl))
                .andExpect(jsonPath("$.shipments[0].deliveredAt").value(nullValue()))
                .andExpect(jsonPath("$.cancellable").value(false));

        // 4. It has arrived.
        action(orderNumber, "/status", "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("DELIVERED"))
                .andExpect(jsonPath("$.actions.ship").value(false))
                .andExpect(jsonPath("$.actions.markDelivered").value(false));
        assertThat(lastEmail(customer).getPayload().subject()).isEqualTo("Your order " + orderNumber + " has been delivered");

        customerView(customer, orderNumber)
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.statusTimeline[*].status").value(contains(
                        "PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.shipments[0].deliveredAt").value(notNullValue()));

        assertThat(auditActions(orderNumber)).containsExactly(
                "ORDER_PLACED", "ORDER_MARKED_PAID", "ORDER_STATUS_CHANGED", "ORDER_SHIPPED", "ORDER_STATUS_CHANGED");
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM audit_log a JOIN users u ON u.id = a.actor_user_id
                        WHERE u.email = ?::citext AND a.action LIKE 'ORDER_%'
                        """, Integer.class, adminEmail))
                .isEqualTo(4);
    }

    // ------------------------------------------------------------------ payment

    @Test
    void onlyBankTransfersAreMarkedAsPaidByHand() throws Exception {
        Customer customer = customer();
        String card = placeOrder(customer, "STRIPE");
        String bank = placeOrder(customer, "BANK_TRANSFER");

        action(card, "/mark-paid", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAID_BY_PROVIDER"));
        mvc.perform(get(ADMIN_ORDERS + "/" + card).cookie(admin))
                .andExpect(jsonPath("$.actions.markPaid").value(false));

        // Not in the future, not long before the order.
        LocalDate today = LocalDate.now(BERLIN);
        for (LocalDate day : List.of(today.plusDays(1), today.minusDays(5))) {
            action(bank, "/mark-paid", "{\"paidOn\":\"%s\"}".formatted(day))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("DATE_INVALID"));
        }
        assertThat(orderStatus(bank)).isEqualTo("PENDING_PAYMENT");

        int emailsBefore = auth.emailsTo(customer.email()).size();
        action(bank, "/mark-paid", "{\"paidOn\":\"%s\",\"notifyCustomer\":false}".formatted(today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.paymentStatus").value("PAID"));
        assertThat(auth.emailsTo(customer.email())).hasSize(emailsBefore);

        action(bank, "/mark-paid", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    // ------------------------------------------------------------------ shipments

    @Test
    void parcelsNeedAPaidOrderAKnownCarrierAndAPlausibleTrackingNumber() throws Exception {
        Customer customer = customer();
        String orderNumber = placeOrder(customer, "BANK_TRANSFER");

        action(orderNumber, "/shipments", "{\"carrier\":\"DHL\",\"trackingNumber\":\"JJD1234567\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        action(orderNumber, "/mark-paid", "{}").andExpect(status().isOk());

        action(orderNumber, "/shipments", "{\"carrier\":\"Owl post\",\"trackingNumber\":\"JJD1234567\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CARRIER_UNKNOWN"));
        for (String number : List.of("abc", "JJD-1234-5678", "<script>1234</script>", "1".repeat(41))) {
            action(orderNumber, "/shipments", "{\"carrier\":\"DHL\",\"trackingNumber\":\"%s\"}".formatted(number))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        action(orderNumber, "/shipments", "{\"carrier\":\"DHL\",\"trackingNumber\":\"JJD1234567\",\"shippedOn\":\"%s\"}"
                        .formatted(LocalDate.now(BERLIN).plusDays(2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_INVALID"));
        // Shipping has its own action; PAID and CANCELLED too.
        for (String status : List.of("SHIPPED", "PAID", "CANCELLED")) {
            action(orderNumber, "/status", "{\"status\":\"%s\"}".formatted(status))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }
        action(orderNumber, "/status", "{\"status\":\"DELIVERED\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(orderStatus(orderNumber)).isEqualTo("PAID");

        // A paid order ships straight away (packing is recorded on the way).
        MvcResult first = action(orderNumber, "/shipments", """
                        {"carrier":"DHL Express","trackingNumber":"1234567890","notifyCustomer":false}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].to").value(contains("PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED")))
                .andExpect(jsonPath("$.order.shipments[0].trackingUrl").value(containsString("tracking-express")))
                .andExpect(jsonPath("$.actions.removeShipment").value(false))
                .andReturn();
        String firstId = read(first, "$.order.shipments[0].id");
        assertThat(auth.emailsTo(customer.email()).stream()
                        .filter(mail -> mail.getPayload().subject().contains("on its way")))
                .isEmpty();

        // The only parcel stays; it can be corrected.
        mvc.perform(delete(ADMIN_ORDERS + "/" + orderNumber + "/shipments/" + firstId).cookie(admin)
                        .with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LAST_SHIPMENT"));
        mvc.perform(json(put(ADMIN_ORDERS + "/" + orderNumber + "/shipments/" + firstId),
                                "{\"carrier\":\"DHL\",\"trackingNumber\":\"JJD7654321\"}").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.shipments[0].carrier").value("DHL"))
                .andExpect(jsonPath("$.order.shipments[0].trackingUrl").value(endsWith("tracking-id=JJD7654321")));

        // A second parcel of a split order; then it turns out to be a mistake.
        MvcResult second = action(orderNumber, "/shipments", "{\"carrier\":\"DHL\",\"trackingNumber\":\"JJD7654322\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.shipments", hasSize(2)))
                .andExpect(jsonPath("$.actions.removeShipment").value(true))
                .andReturn();
        String secondId = read(second, "$.order.shipments[1].id");
        mvc.perform(delete(ADMIN_ORDERS + "/" + orderNumber + "/shipments/" + secondId).cookie(admin)
                        .with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.shipments[*].id").value(contains(firstId)));
        mvc.perform(delete(ADMIN_ORDERS + "/" + orderNumber + "/shipments/" + UUID.randomUUID()).cookie(admin)
                        .with(csrf()).with(uniqueClientIp()))
                .andExpect(status().isNotFound());

        // Shipped orders cannot be cancelled any more.
        action(orderNumber, "/cancel", "{\"reason\":\"Too late\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        assertThat(auditActions(orderNumber)).contains("SHIPMENT_CORRECTED", "SHIPMENT_REMOVED");
        customerView(customer, orderNumber)
                .andExpect(jsonPath("$.shipments", hasSize(1)))
                .andExpect(jsonPath("$.shipments[0].trackingNumber").value("JJD7654321"));
    }

    // ------------------------------------------------------------------ cancelling

    @Test
    void theShopCancelsAPaidOrderAndReturnsTheAnimalsUnlessToldOtherwise() throws Exception {
        Customer customer = customer();
        String restocked = placeOrder(customer, "BANK_TRANSFER");
        String keptBack = placeOrder(customer, "BANK_TRANSFER");
        action(restocked, "/mark-paid", "{}").andExpect(status().isOk());
        int stockBefore = stock(ALPHA_PRODUCT);

        action(restocked, "/cancel", "{\"reason\":\"   \"}")
                .andExpect(status().isBadRequest());
        action(restocked, "/cancel", """
                        {"reason":"Shrimp moulting, not fit to travel","customerMessage":"The colony is moulting right now."}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Shrimp moulting, not fit to travel"))
                .andExpect(jsonPath("$.cancelledAt").value(notNullValue()))
                .andExpect(jsonPath("$.actions.cancel").value(false));
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore + 1);
        OutboxMessage cancelled = lastEmail(customer);
        assertThat(cancelled.getPayload().subject()).isEqualTo("Order " + restocked + " cancelled");
        assertThat(cancelled.getPayload().body())
                .contains("The colony is moulting right now.", "refund the full amount of €12.90")
                .doesNotContain("not fit to travel");
        // The internal reason is not shown to the customer, the message is.
        customerView(customer, restocked)
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(content().string(not(containsString("not fit to travel"))));

        // Animals that died are not put back on sale; the customer is not told by email.
        int emailsBefore = auth.emailsTo(customer.email()).size();
        action(keptBack, "/cancel", "{\"reason\":\"Animals lost\",\"restock\":false,\"notifyCustomer\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore + 1);
        assertThat(auth.emailsTo(customer.email())).hasSize(emailsBefore);

        action(keptBack, "/cancel", "{\"reason\":\"Again\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM audit_log WHERE action = 'ORDER_CANCELLED_BY_SHOP'
                        AND entity_id IN (SELECT id FROM orders WHERE order_number IN (?, ?))
                        """, Integer.class, restocked, keptBack))
                .isEqualTo(2);
    }

    @Test
    void anUnpaidCardOrderCanBeCancelledByTheShop() throws Exception {
        Customer customer = customer();
        String card = placeOrder(customer, "STRIPE");
        int stockBefore = stock(ALPHA_PRODUCT);

        action(card, "/cancel", "{\"reason\":\"Customer asked by phone\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("CANCELLED"));
        assertThat(stock(ALPHA_PRODUCT)).isEqualTo(stockBefore + 1);
        assertThat(lastEmail(customer).getPayload().body()).contains("Nothing is due.");
    }

    // ------------------------------------------------------------------ notes

    @Test
    void internalNotesStayInternal() throws Exception {
        Customer customer = customer();
        String orderNumber = placeOrder(customer, "BANK_TRANSFER");

        action(orderNumber, "/notes", "{\"body\":\"  Customer called: ship on Tuesday.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andExpect(jsonPath("$.notes[0].body").value("Customer called: ship on Tuesday."))
                .andExpect(jsonPath("$.notes[0].author").value("Anna Schmidt"))
                .andExpect(jsonPath("$.notes[0].at").value(notNullValue()));
        action(orderNumber, "/notes", "{\"body\":\"   \"}").andExpect(status().isBadRequest());
        action(orderNumber, "/notes", "{\"body\":\"%s\"}".formatted("x".repeat(2001))).andExpect(status().isBadRequest());

        customerView(customer, orderNumber).andExpect(content().string(not(containsString("ship on Tuesday"))));
        assertThat(auditActions(orderNumber)).contains("ORDER_NOTE_ADDED");
        // Notes go with the order when it is deleted (retention); their author may be deleted first.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM order_notes WHERE order_id = (SELECT id FROM orders WHERE order_number = ?)",
                        Integer.class, orderNumber))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ list

    @Test
    void theListFiltersByTextStatusPaymentAndDay() throws Exception {
        Customer customer = customer();
        String older = placeOrder(customer, "BANK_TRANSFER");
        String newer = placeOrder(customer, "BANK_TRANSFER");
        action(older, "/mark-paid", "{}").andExpect(status().isOk());

        list("q", customer.email())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].orderNumber").value(contains(newer, older)))
                .andExpect(jsonPath("$.items[0].customerName").value("Anna Schmidt"))
                .andExpect(jsonPath("$.items[0].customerEmail").value(customer.email()))
                .andExpect(jsonPath("$.items[0].countryCode").value("DE"))
                .andExpect(jsonPath("$.items[0].itemCount").value(1))
                .andExpect(jsonPath("$.items[0].containsLiveAnimals").value(true))
                .andExpect(jsonPath("$.items[0].total.amount").value(ONE_ANIMAL_TOTAL))
                .andExpect(jsonPath("$.items[0].paymentMethod").value("BANK_TRANSFER"));
        list("q", customer.email(), "oldestFirst", "true")
                .andExpect(jsonPath("$.items[*].orderNumber").value(contains(older, newer)));
        list("q", newer).andExpect(jsonPath("$.items[*].orderNumber").value(contains(newer)));
        // "To ship": paid or being packed.
        list("q", customer.email(), "status", "PAID", "status", "PROCESSING")
                .andExpect(jsonPath("$.items[*].orderNumber").value(contains(older)));
        list("q", customer.email(), "paymentStatus", "UNPAID")
                .andExpect(jsonPath("$.items[*].orderNumber").value(contains(newer)));
        String today = LocalDate.now(BERLIN).toString();
        String tomorrow = LocalDate.now(BERLIN).plusDays(1).toString();
        list("q", customer.email(), "from", today, "to", today).andExpect(jsonPath("$.totalItems").value(2));
        list("q", customer.email(), "from", tomorrow).andExpect(jsonPath("$.totalItems").value(0));
        // LIKE wildcards in the search are taken literally.
        list("q", "%").andExpect(jsonPath("$.totalItems").value(0));
        list("q", customer.email(), "size", "1", "page", "1")
                .andExpect(jsonPath("$.items[*].orderNumber").value(contains(older)))
                .andExpect(jsonPath("$.totalPages").value(2));

        list("from", tomorrow, "to", today)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RANGE_INVALID"));
        list("size", "101").andExpect(status().isBadRequest());
        list("status", "LOST").andExpect(status().isBadRequest());
        list("from", "13.11.2026").andExpect(status().isBadRequest());

        mvc.perform(get(ADMIN_ORDERS + "/PW-2026-999999999").cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get(ADMIN_ORDERS + "/not-an-order").cookie(admin)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private record Customer(String email, Cookie session, String addressId) {}

    /** A verified, signed-in customer named Anna Schmidt with one address in Germany. */
    private Customer customer() throws Exception {
        String email = uniqueEmail();
        Cookie session = auth.registeredSession(email);
        MvcResult address = mvc.perform(json(post("/api/v1/account/addresses"), AddressApiTest.ADDRESS).cookie(session))
                .andExpect(status().isCreated())
                .andReturn();
        return new Customer(email, session, read(address, "$.id"));
    }

    /** Places an order for one live animal with the given payment method. */
    private String placeOrder(Customer customer, String paymentMethod) throws Exception {
        mvc.perform(json(post("/api/v1/cart/items"), "{\"variantId\":\"%s\",\"quantity\":1}".formatted(ALPHA_SINGLE))
                        .cookie(customer.session()))
                .andExpect(status().isOk());
        MvcResult placed = mvc.perform(json(post("/api/v1/checkout/orders"), """
                                {"shippingAddressId": "%s", "shippingMethodCode": "%s", "paymentMethod": "%s",
                                 "acceptedTerms": true, "acceptedLiveAnimalConditions": true,
                                 "expectedTotal": {"amount": %d, "currency": "EUR"}}
                                """.formatted(customer.addressId(), LIVE, paymentMethod, ONE_ANIMAL_TOTAL))
                        .cookie(customer.session())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return read(placed, "$.orderNumber");
    }

    private ResultActions action(String orderNumber, String path, String body) throws Exception {
        return mvc.perform(json(post(ADMIN_ORDERS + "/" + orderNumber + path), body).cookie(admin));
    }

    private ResultActions list(String... params) throws Exception {
        var request = get(ADMIN_ORDERS).cookie(admin);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mvc.perform(request);
    }

    private ResultActions customerView(Customer customer, String orderNumber) throws Exception {
        return mvc.perform(get("/api/v1/account/orders/" + orderNumber).cookie(customer.session()))
                .andExpect(status().isOk());
    }

    private OutboxMessage lastEmail(Customer customer) {
        return auth.emailsTo(customer.email()).getLast();
    }

    private String orderStatus(String orderNumber) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE order_number = ?", String.class, orderNumber);
    }

    private List<String> auditActions(String orderNumber) {
        return jdbc.queryForList("""
                        SELECT action FROM audit_log
                        WHERE entity_id = (SELECT id FROM orders WHERE order_number = ?) ORDER BY created_at, id
                        """, String.class, orderNumber);
    }

    private int stock(String productId) {
        return jdbc.queryForObject("SELECT stock_units FROM products WHERE id = ?::uuid", Integer.class, productId);
    }

    private <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
