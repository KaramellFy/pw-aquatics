package com.pwaquatics.shop.admin;

import static com.pwaquatics.shop.support.AuthFixture.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pwaquatics.shop.support.AuthFixture;
import com.pwaquatics.shop.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dashboard and statistics against hand-counted test data (admin/stats-test-data.sql):
 * every figure below can be checked against the comments in that file.
 */
@IntegrationTest
@Sql(scripts = "/admin/stats-test-data.sql", executionPhase = ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/admin/stats-test-cleanup.sql", executionPhase = ExecutionPhase.AFTER_TEST_CLASS)
class AdminStatisticsApiTest {

    private static final String PERIOD = "?from=2001-01-08&to=2001-01-14";

    @Autowired MockMvc mvc;
    @Autowired AuthFixture auth;
    @Autowired JdbcTemplate jdbc;

    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = auth.adminSession(uniqueEmail());
    }

    @Test
    void onlyAdministratorsSeeStatistics() throws Exception {
        Cookie customer = auth.registeredSession(uniqueEmail());
        for (String path : new String[] {"/api/v1/admin/dashboard", "/api/v1/admin/stats/sales",
                "/api/v1/admin/stats/products", "/api/v1/admin/stats/stock", "/api/v1/admin/stats/sales/export"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        }
    }

    @Test
    void salesFiguresAreComparedWithThePreviousPeriod() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/sales" + PERIOD).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value("2001-01-08"))
                .andExpect(jsonPath("$.period.to").value("2001-01-14"))
                .andExpect(jsonPath("$.period.granularity").value("DAY"))
                .andExpect(jsonPath("$.period.previousFrom").value("2001-01-01"))
                .andExpect(jsonPath("$.period.previousTo").value("2001-01-07"))
                // 3 sales (the cancelled order does not count): 39.90 + 34.90 + 70.00
                .andExpect(jsonPath("$.kpis.revenue.value.amount").value(14480))
                .andExpect(jsonPath("$.kpis.revenue.previous.amount").value(2000))
                .andExpect(jsonPath("$.kpis.revenue.changeBps").value(62400))
                .andExpect(jsonPath("$.kpis.paidRevenue.value.amount").value(10990))
                .andExpect(jsonPath("$.kpis.orders.value").value(3))
                .andExpect(jsonPath("$.kpis.orders.previous").value(1))
                .andExpect(jsonPath("$.kpis.orders.changeBps").value(20000))
                .andExpect(jsonPath("$.kpis.averageOrderValue.value.amount").value(4827))
                .andExpect(jsonPath("$.kpis.averageOrderValue.previous.amount").value(2000))
                // 10 + 10 (one pack of ten) + 20 animals; the food is not an animal
                .andExpect(jsonPath("$.kpis.animalsSold.value").value(40))
                .andExpect(jsonPath("$.kpis.animalsSold.previous").value(5))
                // admins and the registration that is already Monday in Berlin are not counted
                .andExpect(jsonPath("$.kpis.newAccounts.value").value(2))
                .andExpect(jsonPath("$.kpis.newAccounts.previous").value(1))
                .andExpect(jsonPath("$.kpis.returningCustomerShare.valueBps").value(3333))
                .andExpect(jsonPath("$.kpis.returningCustomerShare.previousBps").value(0))
                .andExpect(jsonPath("$.kpis.cancelledOrders.value").value(1))
                .andExpect(jsonPath("$.kpis.cancelledOrders.changeBps").value(nullValue()))
                .andExpect(jsonPath("$.kpis.cancellationRate.valueBps").value(2500))
                .andExpect(jsonPath("$.kpis.cancellationRate.previousBps").value(0));
    }

    @Test
    void theTimeSeriesHasEveryDayAndUsesShopTime() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/sales" + PERIOD).cookie(admin))
                .andExpect(jsonPath("$.series", hasSize(7)))
                .andExpect(jsonPath("$.series[0].start").value("2001-01-08"))
                .andExpect(jsonPath("$.series[0].revenue.amount").value(7480))
                .andExpect(jsonPath("$.series[0].orders").value(2))
                .andExpect(jsonPath("$.series[0].animals").value(20))
                .andExpect(jsonPath("$.series[0].paidRevenue.amount").value(3990))
                .andExpect(jsonPath("$.series[0].awaitingRevenue.amount").value(3490))
                .andExpect(jsonPath("$.series[0].paidOrders").value(1))
                .andExpect(jsonPath("$.series[0].awaitingOrders").value(1))
                .andExpect(jsonPath("$.series[0].awaitingAnimals").value(10))
                // placed at 23:30 UTC on the 10th = 00:30 on the 11th in Berlin
                .andExpect(jsonPath("$.series[2].paidRevenue.amount").value(0))
                .andExpect(jsonPath("$.series[3].start").value("2001-01-11"))
                .andExpect(jsonPath("$.series[3].paidRevenue.amount").value(7000))
                .andExpect(jsonPath("$.series[3].paidAnimals").value(20))
                .andExpect(jsonPath("$.series[4].paidOrders").value(0))
                .andExpect(jsonPath("$.series[4].awaitingOrders").value(0))
                .andExpect(jsonPath("$.byWeekday", hasSize(7)))
                .andExpect(jsonPath("$.byWeekday[0].weekday").value(1))
                .andExpect(jsonPath("$.byWeekday[0].orders").value(2))
                .andExpect(jsonPath("$.byWeekday[0].revenue.amount").value(7480))
                .andExpect(jsonPath("$.byWeekday[3].orders").value(1))
                .andExpect(jsonPath("$.byWeekday[4].orders").value(0))
                .andExpect(jsonPath("$.byStatus[*].status")
                        .value(contains("PENDING_PAYMENT", "PAID", "PROCESSING", "CANCELLED")))
                .andExpect(jsonPath("$.byCountry[*].countryCode").value(contains("DE", "GB")))
                .andExpect(jsonPath("$.byCountry[0].orders").value(2))
                .andExpect(jsonPath("$.byCountry[0].revenue.amount").value(10990));
    }

    @Test
    void longerPeriodsAreGroupedByWeekOrMonth() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/sales?from=2001-01-01&to=2001-03-31").cookie(admin))
                .andExpect(jsonPath("$.period.granularity").value("WEEK"))
                .andExpect(jsonPath("$.series[0].start").value("2001-01-01"))
                .andExpect(jsonPath("$.series[0].paidRevenue.amount").value(2000))
                .andExpect(jsonPath("$.series[1].start").value("2001-01-08"))
                .andExpect(jsonPath("$.series[1].paidRevenue.amount").value(10990))
                .andExpect(jsonPath("$.series[1].awaitingRevenue.amount").value(3490));
        mvc.perform(get("/api/v1/admin/stats/sales?from=2001-01-01&to=2001-12-31").cookie(admin))
                .andExpect(jsonPath("$.period.granularity").value("MONTH"))
                .andExpect(jsonPath("$.series", hasSize(12)))
                .andExpect(jsonPath("$.series[0].start").value("2001-01-01"))
                .andExpect(jsonPath("$.series[0].paidOrders").value(3))
                .andExpect(jsonPath("$.series[1].paidOrders").value(0));
    }

    @Test
    void bestSellersAndRevenueByCategoryAndSpecies() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/products" + PERIOD).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topProducts[*].name").value(contains("Stats Red", "Stats Blue", "Old food")))
                .andExpect(jsonPath("$.topProducts[0].units").value(30))
                .andExpect(jsonPath("$.topProducts[0].packs").value(30))
                .andExpect(jsonPath("$.topProducts[0].revenue.amount").value(9000))
                .andExpect(jsonPath("$.topProducts[0].gradeName").value(nullValue()))
                // the product has no grade (any more): the grade stored with the sale is shown
                .andExpect(jsonPath("$.topProducts[1].gradeName").value("High"))
                .andExpect(jsonPath("$.topProducts[1].units").value(10))
                .andExpect(jsonPath("$.topProducts[1].packs").value(1))
                .andExpect(jsonPath("$.topProducts[2].productId").value(nullValue()))
                .andExpect(jsonPath("$.byCategory[*].name").value(contains("Stats cat A", "Stats cat B", null)))
                .andExpect(jsonPath("$.byCategory[0].revenue.amount").value(9000))
                .andExpect(jsonPath("$.bySpecies[*].name")
                        .value(contains("Statsia rubra", "Statsia caerulea", null)))
                .andExpect(jsonPath("$.bySpecies[2].revenue.amount").value(1000));
        mvc.perform(get("/api/v1/admin/stats/products" + PERIOD + "&limit=1").cookie(admin))
                .andExpect(jsonPath("$.topProducts", hasSize(1)));
        mvc.perform(get("/api/v1/admin/stats/products" + PERIOD + "&limit=0").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stockMovementsShowRestocksSalesAndLosses() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/stock" + PERIOD).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restocked.value").value(50))
                .andExpect(jsonPath("$.restocked.previous").value(20))
                .andExpect(jsonPath("$.restocked.changeBps").value(15000))
                .andExpect(jsonPath("$.sold.value").value(40))
                .andExpect(jsonPath("$.sold.previous").value(5))
                .andExpect(jsonPath("$.lost.value").value(5))
                .andExpect(jsonPath("$.lost.previous").value(1))
                // 5 lost of 45 that left stock; before: 1 of 6
                .andExpect(jsonPath("$.lossRate.valueBps").value(1111))
                .andExpect(jsonPath("$.lossRate.previousBps").value(1667))
                .andExpect(jsonPath("$.byReason[*].reason")
                        .value(contains("RESTOCK", "ORDER_PLACED", "LOSS", "ORDER_CANCELLED")))
                .andExpect(jsonPath("$.byReason[*].units").value(contains(50, -41, -5, 1)));
    }

    @Test
    void dailySalesCanBeDownloadedAsCsv() throws Exception {
        String csv = mvc.perform(get("/api/v1/admin/stats/sales/export" + PERIOD).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"pw-aquatics-sales-2001-01-08-to-2001-01-14.csv\""))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.split("\r\n")).containsExactly(
                "date,orders,paid_orders,revenue_gross_eur,vat_eur,shipping_eur,revenue_net_eur,animals",
                "2001-01-08,2,1,74.80,11.94,19.80,62.86,20",
                "2001-01-09,0,0,0.00,0.00,0.00,0.00,0",
                "2001-01-10,0,0,0.00,0.00,0.00,0.00,0",
                "2001-01-11,1,1,70.00,9.58,0.00,60.42,20",
                "2001-01-12,0,0,0.00,0.00,0.00,0.00,0",
                "2001-01-13,0,0,0.00,0.00,0.00,0.00,0",
                "2001-01-14,0,0,0.00,0.00,0.00,0.00,0");
    }

    @Test
    void invalidPeriodsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/sales?from=2001-01-14&to=2001-01-08").cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STATS_RANGE_INVALID"));
        mvc.perform(get("/api/v1/admin/stats/stock?from=2001-01-01&to=2003-01-02").cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STATS_RANGE_INVALID"));
        mvc.perform(get("/api/v1/admin/stats/sales?from=yesterday").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withoutDatesTheLastThirtyDaysAreShown() throws Exception {
        mvc.perform(get("/api/v1/admin/stats/sales").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.granularity").value("DAY"))
                .andExpect(jsonPath("$.series", hasSize(30)));
    }

    @Test
    void theDashboardMatchesTheDatabase() throws Exception {
        Long awaiting = jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'PENDING_PAYMENT'", Long.class);
        Long awaitingCents = jdbc.queryForObject(
                "SELECT coalesce(sum(total_cents), 0) FROM orders WHERE status = 'PENDING_PAYMENT'", Long.class);
        Long toShip = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE status IN ('PAID', 'PROCESSING')", Long.class);
        Long live = jdbc.queryForObject("SELECT count(*) FROM products WHERE status = 'ACTIVE'", Long.class);
        mvc.perform(get("/api/v1/admin/dashboard").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awaitingPaymentOrders").value(awaiting))
                .andExpect(jsonPath("$.awaitingPayment.amount").value(awaitingCents))
                .andExpect(jsonPath("$.ordersToShip").value(toShip))
                .andExpect(jsonPath("$.liveProducts").value(live))
                .andExpect(jsonPath("$.recentOrders.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(8)))
                .andExpect(jsonPath("$.lowStock.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(8)));
    }
}
