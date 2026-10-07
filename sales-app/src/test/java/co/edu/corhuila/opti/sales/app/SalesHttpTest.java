package co.edu.corhuila.opti.sales.app;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Contract checks of work orders plus the invoice, payment and transition rules of this domain. */
class SalesHttpTest extends ContractChecks {

    private static final String PATIENT = "11111111-1111-4111-8111-111111111111";

    @Override
    protected String collectionPath() {
        return "/api/v1/work-orders";
    }

    /** Only the workflow opens orders, so the contract checks run as the service identity. */
    @Override
    protected String writerRole() {
        return "SERVICE";
    }

    @Override
    protected String validBody(int n) {
        return orderJson("saga-" + n, 52_000_000L, 2);
    }

    @Override
    protected String invalidBody() {
        return """
                {"reference":" ","items":[{"sku":"x","description":"","quantity":0,"unitPriceCents":-1}]}""";
    }

    @Override
    protected List<String> invalidBodyFields() {
        return List.of("patientId", "reference", "items[0].productType", "items[0].productId",
                "items[0].reservationId", "items[0].sku", "items[0].description", "items[0].quantity",
                "items[0].unitPriceCents");
    }

    // ---- opening --------------------------------------------------------------------------

    @Test
    void anAdminOrSellerCannotOpenOrdersBecauseThePriceComesFromTheWorkflow() throws Exception {
        as(create(validBody(next()), "key-" + UUID.randomUUID()), "ADMIN").andExpect(status().isForbidden());
        as(create(validBody(next()), "key-" + UUID.randomUUID()), "SELLER").andExpect(status().isForbidden());
    }

    @Test
    void openingReturnsTheOrderWithNumberTotalAndItsInvoice() throws Exception {
        String id = open(orderJson("saga-x", 52_000_000L, 2));

        as(get(collectionPath() + "/" + id), "SELLER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTATION"))
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("OT-")))
                .andExpect(jsonPath("$.totalCents").value(104_000_000))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].subtotalCents").value(104_000_000))
                .andExpect(jsonPath("$.items[0].reservationId").exists());
        as(get("/api/v1/invoices?workOrderId=" + id), "SELLER")
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].balanceCents").value(104_000_000));
    }

    @Test
    void moneyWithDecimalsIsRejected() throws Exception {
        as(create(orderJson("saga-dec", 52_000_000L, 1).replace("52000000", "520000.5"),
                "key-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("items[0].unitPriceCents")));
    }

    @Test
    void anInvalidUuidInsideALineNamesTheNestedField() throws Exception {
        as(create(orderJson("saga-uuid", 1000L, 1).replace(PATIENT, "nope"), "key-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("patientId")));
    }

    @Test
    void anOrderWithoutLinesIsRejected() throws Exception {
        as(create("{\"patientId\":\"" + PATIENT + "\",\"reference\":\"saga\",\"items\":[]}",
                "key-" + UUID.randomUUID()), "SERVICE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("items")));
    }

    // ---- transitions ----------------------------------------------------------------------

    @Test
    void approveAndAdvanceWalkTheLifecycleAndRejectInvalidMoves() throws Exception {
        String id = open(orderJson("saga-life", 1000L, 1));

        as(post(collectionPath() + "/" + id + "/advance"), "SELLER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        as(post(collectionPath() + "/" + id + "/approve"), "SELLER")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        as(post(collectionPath() + "/" + id + "/approve"), "SELLER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        for (String next : List.of("IN_LABORATORY", "READY", "DELIVERED")) {
            if (next.equals("DELIVERED")) {
                as(post(collectionPath() + "/" + id + "/advance"), "SELLER")
                        .andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
                as(payment(invoiceOf(id), "{\"amountCents\":1000,\"method\":\"CASH\"}", "pay-" + UUID.randomUUID()), "SELLER")
                        .andExpect(status().isCreated());
            }
            as(post(collectionPath() + "/" + id + "/advance"), "SELLER")
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        as(post(collectionPath() + "/" + id + "/cancel"), "SELLER").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void transitionsNeedAnAllowedRole() throws Exception {
        String id = open(orderJson("saga-role", 1000L, 1));

        as(post(collectionPath() + "/" + id + "/approve"), "OPTOMETRIST").andExpect(status().isForbidden());
        as(post(collectionPath() + "/" + id + "/approve"), "SERVICE").andExpect(status().isForbidden());
        as(post(collectionPath() + "/" + id + "/advance"), "OPTOMETRIST").andExpect(status().isForbidden());
    }

    @Test
    void cancelIsIdempotentAndVoidsTheInvoice() throws Exception {
        String id = open(orderJson("saga-cancel", 1000L, 1));

        as(post(collectionPath() + "/" + id + "/cancel"), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        as(post(collectionPath() + "/" + id + "/cancel"), "SERVICE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        as(get("/api/v1/invoices?workOrderId=" + id), "SELLER").andExpect(jsonPath("$.data[0].status").value("VOID"));
        as(post(collectionPath() + "/" + UUID.randomUUID() + "/cancel"), "SERVICE").andExpect(status().isNotFound());
    }

    @Test
    void statusAndDateFiltersAreValidated() throws Exception {
        as(get(collectionPath() + "?status=NOPE"), "ADMIN").andExpect(status().isBadRequest());
        as(get(collectionPath() + "?createdBefore=yesterday"), "ADMIN").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("createdBefore"));
        as(get(collectionPath() + "?patientId=nope"), "ADMIN").andExpect(status().isBadRequest());
        as(get(collectionPath() + "?status=QUOTATION&createdBefore=2999-01-01T00:00:00Z"), "ADMIN")
                .andExpect(status().isOk());
    }

    // ---- invoices and payments ------------------------------------------------------------

    @Test
    void paymentsReduceTheBalanceUntilPaidAndAreIdempotent() throws Exception {
        String invoiceId = invoiceOf(open(orderJson("saga-pay", 100_000L, 1)));
        String key = "pay-" + UUID.randomUUID();

        as(payment(invoiceId, "{\"amountCents\":60000,\"method\":\"CASH\",\"reference\":\"r-1\"}", key), "SELLER")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/payments/")));
        as(payment(invoiceId, "{\"amountCents\":60000,\"method\":\"CASH\",\"reference\":\"r-1\"}", key), "SELLER")
                .andExpect(status().isOk());
        as(get("/api/v1/invoices/" + invoiceId), "SELLER")
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.paidCents").value(60_000))
                .andExpect(jsonPath("$.balanceCents").value(40_000));
        as(payment(invoiceId, "{\"amountCents\":40000,\"method\":\"NEQUI\"}", "pay-" + UUID.randomUUID()), "ADMIN")
                .andExpect(status().isCreated());
        as(get("/api/v1/invoices/" + invoiceId), "SELLER").andExpect(jsonPath("$.status").value("PAID"));
        as(get("/api/v1/invoices/" + invoiceId + "/payments"), "SELLER")
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void paymentRulesAreEnforced() throws Exception {
        String invoiceId = invoiceOf(open(orderJson("saga-rules", 100_000L, 1)));

        as(payment(invoiceId, "{\"amountCents\":100001,\"method\":\"CASH\"}", "pay-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
        as(payment(invoiceId, "{\"amountCents\":0,\"method\":\"BITCOIN\"}", "pay-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("method")));
        as(payment(invoiceId, "{\"amountCents\":1000,\"method\":\"CASH\"}", "short"), "SELLER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("Idempotency-Key")));
        as(payment(invoiceId, "{\"amountCents\":10.5,\"method\":\"CASH\"}", "pay-" + UUID.randomUUID()), "SELLER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("amountCents")));
        as(payment(invoiceId, "{\"amountCents\":1000,\"method\":\"CASH\"}", "pay-" + UUID.randomUUID()), "OPTOMETRIST")
                .andExpect(status().isForbidden());
        as(payment(UUID.randomUUID().toString(), "{\"amountCents\":1000,\"method\":\"CASH\"}",
                "pay-" + UUID.randomUUID()), "SELLER").andExpect(status().isNotFound());
    }

    @Test
    void anOrderWithPaymentsCannotBeCancelled() throws Exception {
        String orderId = open(orderJson("saga-paid", 100_000L, 1));
        as(payment(invoiceOf(orderId), "{\"amountCents\":1000,\"method\":\"CASH\"}", "pay-" + UUID.randomUUID()),
                "SELLER").andExpect(status().isCreated());

        as(post(collectionPath() + "/" + orderId + "/cancel"), "SERVICE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void invoiceListFiltersAreValidated() throws Exception {
        as(get("/api/v1/invoices?status=NOPE"), "ADMIN").andExpect(status().isBadRequest());
        as(get("/api/v1/invoices?colour=red"), "ADMIN").andExpect(status().isBadRequest());
        as(get("/api/v1/invoices/not-a-uuid"), "ADMIN").andExpect(status().isBadRequest());
        as(get("/api/v1/invoices/" + UUID.randomUUID()), "ADMIN").andExpect(status().isNotFound());
    }

    // ---- helpers --------------------------------------------------------------------------

    private String open(String body) throws Exception {
        return idOf(as(create(body, "key-" + UUID.randomUUID()), "SERVICE").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private String invoiceOf(String orderId) throws Exception {
        String page = as(get("/api/v1/invoices?workOrderId=" + orderId), "ADMIN").andReturn().getResponse()
                .getContentAsString();
        return idOf(page.substring(page.indexOf("\"data\"")));
    }

    private static MockHttpServletRequestBuilder payment(String invoiceId, String body, String key) {
        return post("/api/v1/invoices/" + invoiceId + "/payments").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String orderJson(String reference, long unitPriceCents, int quantity) {
        return """
                {"patientId":"%s","reference":"%s","items":[{"productType":"FRAME","productId":"%s",
                 "reservationId":"%s","sku":"RB5228-2000",
                 "description":"Frame Ray-Ban RB5228","quantity":%d,"unitPriceCents":%d}]}"""
                .formatted(PATIENT, reference, UUID.randomUUID(), UUID.randomUUID(), quantity, unitPriceCents);
    }
}
