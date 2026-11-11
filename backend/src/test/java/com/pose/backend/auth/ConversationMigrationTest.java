package com.pose.backend.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationMigrationTest {
    @Test
    void v13CreatesTenantScopedConversationStorageDeduplicationAndLeastPrivilegeGrants() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:conversation" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var locations = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/auth-test-baseline", "classpath:db/migration");
        locations.target("12").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        Timestamp now = Timestamp.from(Instant.parse("2026-11-11T00:00:00Z"));
        UUID orgOne = UUID.randomUUID(), orgTwo = UUID.randomUUID();
        UUID adminRole = UUID.randomUUID(), managerRole = UUID.randomUUID(), salesRole = UUID.randomUUID();
        UUID careRole = UUID.randomUUID(), dataRole = UUID.randomUUID();
        UUID userOne = UUID.randomUUID(), userTwo = UUID.randomUUID();
        UUID account = UUID.randomUUID(), conversation = UUID.randomUUID(), message = UUID.randomUUID();
        for (UUID org : new UUID[]{orgOne, orgTwo}) {
            jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?, 'Org',?,?)",
                    org, org.toString(), now, now);
        }
        for (UUID user : new UUID[]{userOne, userTwo}) {
            UUID org = user.equals(userOne) ? orgOne : orgTwo;
            jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?, 'User',?,?,?,?)",
                    user, org, user + "@example.test", user + "@example.test", now, now);
        }
        insertRole(jdbc, orgOne, adminRole, "ADMIN", now);
        insertRole(jdbc, orgOne, managerRole, "MANAGER", now);
        insertRole(jdbc, orgOne, salesRole, "SALES_REP", now);
        insertRole(jdbc, orgOne, careRole, "CUSTOMER_CARE", now);
        insertRole(jdbc, orgOne, dataRole, "DATA_STAFF", now);

        Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/auth-test-baseline", "classpath:db/migration")
                .load().migrate();

        jdbc.update("INSERT INTO channel_accounts(id,organization_id,channel_type,display_name,external_account_id,secret_ref,created_at,updated_at) VALUES (?,?, 'TELEGRAM','Demo bot','bot-1','POSE_CHANNEL_TELEGRAM_WEBHOOK_SECRET',?,?)",
                account, orgOne, now, now);
        jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,created_at,updated_at) VALUES (?,?,?,'chat-1',?,?)",
                conversation, orgOne, account, now, now);
        jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,?,?,'INBOUND','message-1','TEXT','hello','RECEIVED',?,?)",
                message, orgOne, conversation, now, now);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversations WHERE id=? AND lead_id IS NULL AND customer_id IS NULL AND assigned_user_id IS NULL",
                Integer.class, conversation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code='ADMIN' AND p.code='channel.manage'",
                Integer.class, orgOne)).isEqualTo(1);
        for (String role : new String[]{"ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code=? AND p.code='conversation.read'",
                    Integer.class, orgOne, role)).as(role + " can read conversations").isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id WHERE r.organization_id=? AND r.code='DATA_STAFF' AND p.code LIKE 'conversation.%'",
                Integer.class, orgOne)).isZero();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO conversation_messages(id,organization_id,conversation_id,direction,external_message_id,content_type,content,delivery_status,occurred_at,created_at) VALUES (?,? ,?,'INBOUND','message-1','TEXT','duplicate','RECEIVED',?,?)",
                UUID.randomUUID(), orgOne, conversation, now, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO conversations(id,organization_id,channel_account_id,external_thread_id,created_at,updated_at) VALUES (?,?,?,'cross-tenant',?,?)",
                UUID.randomUUID(), orgTwo, account, now, now)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertRole(JdbcTemplate jdbc, UUID organizationId, UUID id, String code, Timestamp now) {
        jdbc.update("INSERT INTO roles(id,organization_id,code,display_name,created_at) VALUES (?,?,?,?,?)",
                id, organizationId, code, code, now);
    }
}
