package com.pose.backend.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.audit.FoundationAuditRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelWebhookServiceTest {
    private final UUID organizationId = UUID.randomUUID();
    private final UUID telegramAccountId = UUID.randomUUID();
    private final UUID zaloAccountId = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private ChannelWebhookService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:channel" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/auth-test-baseline", "classpath:db/migration")
                .load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        Instant now = Instant.parse("2026-11-11T00:00:00Z");
        Timestamp timestamp = Timestamp.from(now);
        jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,'edu','Education',?,?)",
                organizationId, timestamp, timestamp);
        jdbc.update("""
                INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,
                    status,secret_ref,created_at,updated_at)
                VALUES (?,?,'TELEGRAM','Telegram demo','demo-bot','ACTIVE','POSE_TEST_TELEGRAM_SECRET',?,?),
                       (?,?,'ZALO_SIM','Zalo demo','zalo-app-edu','ACTIVE','POSE_TEST_ZALO_SECRET',?,?)
                """, telegramAccountId, organizationId, timestamp, timestamp,
                zaloAccountId, organizationId, timestamp, timestamp);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("POSE_TEST_TELEGRAM_SECRET", "telegram-test-secret")
                .withProperty("POSE_TEST_ZALO_SECRET", "zalo-test-secret");
        service = new ChannelWebhookService(jdbc, environment,
                new FoundationAuditRepository(jdbc, new ObjectMapper()), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void telegramWrongSecretWritesNothingAndValidRetryIsIdempotent() {
        var payload = new ObjectMapper().createObjectNode();
        payload.put("update_id", 1234);
        var message = payload.putObject("message");
        message.put("message_id", 55);
        message.put("date", 1794355200);
        message.putObject("chat").put("id", -10077);
        message.putObject("from").put("id", 888);
        message.put("text", "Xin chào POSE");

        assertThatThrownBy(() -> service.receive("telegram", "wrong", payload))
                .isInstanceOfSatisfying(ChannelWebhookException.class,
                        exception -> assertThat(exception.status()).isEqualTo(401));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='MESSAGE_RECEIVED'", Integer.class)).isZero();

        ChannelWebhookAck first = service.receive("telegram", "telegram-test-secret", payload);
        ChannelWebhookAck retry = service.receive("telegram", "telegram-test-secret", payload);
        assertThat(first.ok()).isTrue();
        assertThat(first.duplicate()).isFalse();
        assertThat(retry.duplicate()).isTrue();
        assertThat(retry.conversationId()).isEqualTo(first.conversationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE channel_account_id=? AND external_thread_id='-10077'",
                Integer.class, telegramAccountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE external_message_id='55' AND content='Xin chào POSE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action='MESSAGE_RECEIVED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void zaloSimulatorAcceptsOaShapedPayloadAndKeepsConversationUnlinked() {
        var payload = new ObjectMapper().createObjectNode();
        payload.put("event_name", "user_send_text");
        payload.put("timestamp", "2026-11-11T10:00:00Z");
        payload.put("app_id", "zalo-app-edu");
        payload.putObject("sender").put("id", "zalo-user-21");
        payload.putObject("recipient").put("id", "zalo-app-edu");
        var message = payload.putObject("message");
        message.put("msg_id", "zalo-msg-31");
        message.put("text", "Tin nhắn mô phỏng");

        ChannelWebhookAck ack = service.receive("zalo-sim", "zalo-test-secret", payload);
        assertThat(ack.duplicate()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE channel_account_id=? AND external_thread_id='zalo-user-21' AND lead_id IS NULL AND customer_id IS NULL AND assigned_user_id IS NULL",
                Integer.class, zaloAccountId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversation_messages WHERE external_message_id='zalo-msg-31' AND content='Tin nhắn mô phỏng'",
                Integer.class)).isEqualTo(1);
    }
}
