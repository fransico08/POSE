package com.pose.backend.auth;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiError;
import com.pose.backend.api.ApiExceptionHandler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "pose.sales-assignment.expiry-scan-delay-ms=86400000")
class W17SafeErrorLoggingIntegrationTest extends FoundationTestSupport {
    private static final String FAKE_NAME = "Canary Customer W17";
    private static final String FAKE_EMAIL = "canary-w17-secret@example.test";
    private static final String FAKE_PHONE = "0909888777";
    private static final String FAKE_PROMPT = "SENSITIVE_PROMPT_PAYLOAD_W17";

    @Autowired ApiExceptionHandler exceptionHandler;

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
    }

    @Test
    void unexpectedExceptionDoesNotLogSensitiveMessageOrThrowableStackTrace() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
            IllegalStateException exception = new IllegalStateException(
                    "prompt=" + FAKE_PROMPT + " email=" + FAKE_EMAIL + " phone=" + FAKE_PHONE);

            ResponseEntity<ApiError> response = exceptionHandler.unexpected(exception, request);

            assertThat(response.getStatusCode().value()).isEqualTo(500);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().traceId()).isNotBlank();
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage())
                    .contains("traceId=" + response.getBody().traceId())
                    .contains("exception=IllegalStateException")
                    .contains("sqlState=none")
                    .contains("constraint=none")
                    .doesNotContain(FAKE_PROMPT, FAKE_EMAIL, FAKE_PHONE);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void framework4xxErrorResponseLogsAtWarnNotErrorWithoutSensitiveData() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");
            var exception = new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "email=" + FAKE_EMAIL + " phone=" + FAKE_PHONE);

            ResponseEntity<ApiError> response = exceptionHandler.unexpected(exception, request);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().traceId()).isNotBlank();
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getFormattedMessage())
                    .contains("status=400")
                    .contains("traceId=" + response.getBody().traceId())
                    .contains("exception=ResponseStatusException")
                    .doesNotContain(FAKE_EMAIL, FAKE_PHONE);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    @Test
    void postgresCheckAndUniqueViolationsLogOnlySafeMetadataAndNeverLeakRowValues() throws Exception {
        Assumptions.assumeTrue(isPostgres(), "PostgreSQL-only CHECK/UNIQUE constraint log redaction test");
        for (String p : List.of("customer.read", "customer.write", "offering.read")) {
            if (jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code=?", Integer.class, p) == 0) {
                permission(p);
            }
            if (jdbc.queryForObject("""
                    SELECT count(*) FROM role_permissions rp
                    JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id
                    WHERE r.organization_id=? AND r.code='SALES_REP' AND p.code=?
                    """, Integer.class, ORG_ONE, p) == 0) {
                grant(ORG_ONE, "SALES_REP", p);
            }
        }
        UUID salesUser = id(4201);
        UUID cat = id(4202);
        UUID offer = id(4203);
        user(salesUser, ORG_ONE, "W17 Log Sales", "w17-log-sales@example.test", hashOne);
        userRole(ORG_ONE, salesUser, "SALES_REP");
        jdbc.update("INSERT INTO team_memberships(id,organization_id,team_id,user_id,joined_at,status) VALUES (?,?,?,?,?,'ACTIVE')",
                id(4204), ORG_ONE, TEAM_MANAGED, salesUser, time());
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,status,created_at,updated_at) VALUES (?,?,'W17L','W17L','ACTIVE',?,?)",
                cat, ORG_ONE, time(), time());
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,'W17L-OFF','W17L Offer','SERVICE','ACTIVE',?,?)",
                offer, ORG_ONE, cat, TEAM_MANAGED, time(), time());
        jdbc.update("INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,status,effective_from,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?,?)",
                UUID.randomUUID(), ORG_ONE, offer, salesUser, USER_ONE, time(), time(), time());
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ch.qos.logback.classic.Level previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        jdbc.execute("ALTER TABLE customers ADD CONSTRAINT w17_check_canary CHECK (display_name <> '" + FAKE_NAME + "')");
        try {
            // 1) CHECK violation through real HTTP API endpoint
            JsonNode sales = response(login("one", "w17-log-sales@example.test", PASSWORD_ONE), 200);
            String traceIdCheck = jsonRequest(post("/api/v1/customers"), sales, Map.of(
                    "displayName", FAKE_NAME,
                    "email", FAKE_EMAIL,
                    "phone", FAKE_PHONE,
                    "customerType", "INDIVIDUAL",
                    "relatedOfferingId", offer.toString()
            ))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andReturn().getResponse().getHeader("X-Trace-Id");

            assertThat(appender.list).hasSize(1);
            ILoggingEvent checkEvent = appender.list.getFirst();
            assertThat(checkEvent.getThrowableProxy()).isNull();
            assertThat(checkEvent.getFormattedMessage())
                    .contains("traceId=" + traceIdCheck)
                    .contains("sqlState=23514")
                    .contains("constraint=w17_check_canary")
                    .doesNotContain(FAKE_NAME, FAKE_EMAIL, FAKE_PHONE);

            // 2) UNIQUE violation on PostgreSQL (both dataConflict and unexpected paths)
            appender.list.clear();
            jdbc.update("""
                    INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,created_at,updated_at)
                    VALUES (?,?,?,'First Safe',?,?,?,?,'ACTIVE',?,?)
                    """, UUID.randomUUID(), ORG_ONE, USER_ONE, FAKE_EMAIL, FAKE_EMAIL, FAKE_PHONE, FAKE_PHONE, time(), time());

            DataIntegrityViolationException uniqueEx = null;
            try {
                jdbc.update("""
                        INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,phone,normalized_phone,status,created_at,updated_at)
                        VALUES (?,?,?,'Second Safe',?,?,?,?,'ACTIVE',?,?)
                        """, UUID.randomUUID(), ORG_ONE, USER_ONE, FAKE_EMAIL, FAKE_EMAIL, FAKE_PHONE, FAKE_PHONE, time(), time());
            } catch (DataIntegrityViolationException ex) {
                uniqueEx = ex;
            }
            assertThat(uniqueEx).isNotNull();

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
            ResponseEntity<ApiError> conflictResp = exceptionHandler.dataConflict(uniqueEx, request);
            ResponseEntity<ApiError> unexpectedResp = exceptionHandler.unexpected(new RuntimeException("wrapper", uniqueEx), request);

            assertThat(conflictResp.getStatusCode().value()).isEqualTo(409);
            assertThat(conflictResp.getBody().code()).isEqualTo("CONFLICT");
            assertThat(unexpectedResp.getStatusCode().value()).isEqualTo(500);
            assertThat(unexpectedResp.getBody().code()).isEqualTo("INTERNAL_ERROR");

            assertThat(appender.list).hasSize(2);
            for (ILoggingEvent event : appender.list) {
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage())
                        .contains("sqlState=23505")
                        .contains("constraint=uq_customers_org_email")
                        .doesNotContain(FAKE_NAME, FAKE_EMAIL, FAKE_PHONE);
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            jdbc.execute("ALTER TABLE customers DROP CONSTRAINT IF EXISTS w17_check_canary");
        }
    }
}

