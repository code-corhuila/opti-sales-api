package co.edu.corhuila.opti.sales.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.corhuila.opti.sales.testsupport.TestClock;

/**
 * The contract checks of Annex C, written once: authentication, error envelope, correlation,
 * validation at the border, idempotent creation and bounded listings. Each service extends this
 * class and says which resource to exercise; its own rules go in a separate test.
 */
@SpringBootTest(classes = HttpTestApplication.class)
@AutoConfigureMockMvc
abstract class ContractChecks {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected TestClock clock;
    @Autowired
    private ObjectMapper json;

    /** Collection path of the resource under test, for example {@code /api/v1/frames}. */
    protected abstract String collectionPath();

    /** A role that may create and read the resource. */
    protected abstract String writerRole();

    /** A role with no permission at all on the resource. */
    protected String strangerRole() {
        return "NOBODY";
    }

    /** A valid, unique creation body ({@code n} makes it unique). */
    protected abstract String validBody(int n);

    /** A body with several invalid fields, and the names the error must report. */
    protected abstract String invalidBody();

    protected abstract List<String> invalidBodyFields();

    protected int next() {
        return SEQUENCE.incrementAndGet();
    }

    // ---- authentication -------------------------------------------------------------------

    @Test
    void healthNeedsNoToken() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void requestWithoutTokenIsUnauthorizedWithTheEnvelope() throws Exception {
        mvc.perform(get(collectionPath()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void brokenTokensAreAllRejected() throws Exception {
        var now = clock.instant();
        for (String token : List.of(TestTokens.algNone(now), TestTokens.hs256WithPublicKey(now),
                TestTokens.expired(now), TestTokens.signedByOtherKey(now), TestTokens.withoutSubject(now),
                "not.a.jwt", "")) {
            mvc.perform(get(collectionPath()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
        mvc.perform(get(collectionPath()).header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    // ---- envelope and correlation ---------------------------------------------------------

    @Test
    void malformedIdIsAValidationErrorNamingTheField() throws Exception {
        as(get(collectionPath() + "/not-a-uuid"), writerRole())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));
    }

    @Test
    void missingResourceIsNotFound() throws Exception {
        as(get(collectionPath() + "/" + UUID.randomUUID()), writerRole())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void traceIdRepeatsTheReceivedCorrelationIdAndIsReturnedInTheHeader() throws Exception {
        as(get(collectionPath() + "/" + UUID.randomUUID()).header("X-Correlation-Id", "e2e-test-1"), writerRole())
                .andExpect(header().string("X-Correlation-Id", "e2e-test-1"))
                .andExpect(jsonPath("$.traceId").value("e2e-test-1"));
    }

    @Test
    void correlationIdIsGeneratedWhenMissingOrUnsafe() throws Exception {
        as(get(collectionPath()), writerRole())
                .andExpect(header().string("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}")));
        as(get(collectionPath()).header("X-Correlation-Id", "bad id with spaces\t"), writerRole())
                .andExpect(header().string("X-Correlation-Id", not(containsString(" "))));
    }

    @Test
    void unknownRouteAnswersWithTheEnvelope() throws Exception {
        as(get("/api/v1/nothing-here"), writerRole())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ---- validation at the border ---------------------------------------------------------

    @Test
    void malformedJsonIsAValidationError() throws Exception {
        as(create("{ this is not json", "key-malformed-1"), writerRole())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void unknownBodyFieldIsRejectedNamingTheField() throws Exception {
        String body = validBody(next()).replaceFirst("\\{", "{\"unexpectedField\":1,");
        as(create(body, "key-" + UUID.randomUUID()), writerRole())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("unexpectedField")));
    }

    @Test
    void invalidBodyNamesEveryFieldIncludingTheIdempotencyKeyHeader() throws Exception {
        ResultActions result = as(post(collectionPath()).contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody()), writerRole())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("Idempotency-Key")));
        for (String field : invalidBodyFields()) {
            result.andExpect(jsonPath("$.details[*].field", hasItem(field)));
        }
    }

    // ---- idempotent creation --------------------------------------------------------------

    @Test
    void createReturns201WithLocationAndRetryReturns200WithTheSameId() throws Exception {
        String key = "key-" + UUID.randomUUID();
        String body = validBody(next());

        String id = idOf(as(create(body, key), writerRole())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(collectionPath())))
                .andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")))
                .andReturn().getResponse().getContentAsString());

        as(create(body, key), writerRole())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void tokenWithoutTheRightRoleIsForbidden() throws Exception {
        as(create(validBody(next()), "key-" + UUID.randomUUID()), strangerRole())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ---- listings -------------------------------------------------------------------------

    @Test
    void listWithoutLimitUsesTwentyAndMetaRepeatsPageAndLimit() throws Exception {
        as(get(collectionPath()), writerRole())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void invalidPaginationOrUnknownFilterIsRejected() throws Exception {
        as(get(collectionPath() + "?limit=101"), writerRole()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("limit"));
        as(get(collectionPath() + "?page=0"), writerRole()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("page"));
        as(get(collectionPath() + "?limit=abc"), writerRole()).andExpect(status().isBadRequest());
        as(get(collectionPath() + "?colour=red"), writerRole()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("colour"));
    }

    @Test
    void listIsBoundedByLimitAndNewestFirst() throws Exception {
        for (int i = 0; i < 3; i++) {
            as(create(validBody(next()), "key-" + UUID.randomUUID()), writerRole()).andExpect(status().isCreated());
            clock.advance(Duration.ofMinutes(1));
        }

        String page = as(get(collectionPath() + "?limit=2"), writerRole())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = json.readTree(page).path("data");
        assertThat(data.size()).isLessThanOrEqualTo(2).isPositive();
        if (data.size() == 2) {
            assertThat(data.get(0).path("createdAt").asText())
                    .isGreaterThanOrEqualTo(data.get(1).path("createdAt").asText());
        }
        assertThat(json.readTree(page).path("meta").path("total").asLong()).isGreaterThanOrEqualTo(3);
    }

    // ---- helpers --------------------------------------------------------------------------

    protected ResultActions as(MockHttpServletRequestBuilder request, String... roles) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + TestTokens.valid(clock.instant(), roles)));
    }

    protected MockHttpServletRequestBuilder create(String body, String key) {
        return post(collectionPath()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String idOf(String responseBody) {
        int start = responseBody.indexOf("\"id\":\"") + 6;
        return responseBody.substring(start, responseBody.indexOf('"', start));
    }
}
