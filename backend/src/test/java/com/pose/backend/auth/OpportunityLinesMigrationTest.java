package com.pose.backend.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OpportunityLinesMigrationTest {
    @Test void legacyOpportunitiesAreBackfilledAndTenantAndLineConstraintsApply() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:opportunity" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(ds).locations("classpath:db/auth-test-baseline", "classpath:db/migration").target("9").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        Timestamp now = Timestamp.from(Instant.parse("2026-11-02T18:30:00Z"));
        UUID org = UUID.randomUUID(), other = UUID.randomUUID(), user = UUID.randomUUID();
        UUID category = UUID.randomUUID(), otherCategory = UUID.randomUUID(), offering = UUID.randomUUID(), otherOffering = UUID.randomUUID();
        UUID customer = UUID.randomUUID(), won = UUID.randomUUID(), open = UUID.randomUUID();
        for (UUID organization : new UUID[]{org, other})
            jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?, 'Org',?,?)", organization, organization.toString(), now, now);
        jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?,'User','a@b.test','a@b.test',?,?)", user, org, now, now);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,created_at,updated_at) VALUES (?,?,'CAT','Category',?,?)", category, org, now, now);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,created_at,updated_at) VALUES (?,?,'CAT','Category',?,?)", otherCategory, other, now, now);
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'OFF','Offering','SERVICE','ACTIVE',?,?)", offering, org, category, now, now);
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'OFF','Offering','SERVICE','ACTIVE',?,?)", otherOffering, other, otherCategory, now, now);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','c@b.test','c@b.test','ACTIVE',?,?)", customer, org, user, now, now);
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,closing_reason,closed_at,created_at,updated_at) VALUES (?,?,?,?,?,'Won','WON',1234.50,'Reason',?,?,?)", won, org, customer, offering, user, now, now, now);
        jdbc.update("INSERT INTO opportunities(id,organization_id,customer_id,offering_id,owner_user_id,title,stage,expected_value,created_at,updated_at) VALUES (?,?,?,?,?,'Open','NEW',987.65,?,?)", open, org, customer, offering, user, now, now);
        Flyway.configure().dataSource(ds).locations("classpath:db/auth-test-baseline", "classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM opportunity_lines", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT line_total FROM opportunity_lines WHERE id=? AND opportunity_id=?", BigDecimal.class, won, won)).isEqualByComparingTo("1234.50");
        assertThat(jdbc.queryForObject("SELECT expected_value FROM opportunities WHERE id=?", BigDecimal.class, open)).isEqualByComparingTo("987.65");
        assertThat(jdbc.queryForObject("SELECT final_value FROM opportunities WHERE id=?", BigDecimal.class, won)).isEqualByComparingTo("1234.50");
        assertThat(jdbc.queryForObject("SELECT actual_close_date FROM opportunities WHERE id=?", LocalDate.class, won)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThatThrownBy(() -> jdbc.update("UPDATE opportunities SET stage='CANCELLED',closed_at=? WHERE id=?", now, open)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,discount_amount,line_total,created_at,updated_at) VALUES (?,?,?,?,2,1,0,0,0,?,?)", UUID.randomUUID(), org, open, otherOffering, now, now)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO opportunity_lines(id,organization_id,opportunity_id,offering_id,position,quantity,unit_price,discount_amount,line_total,created_at,updated_at) VALUES (?,?,?,?,2,1,0,0,0,?,?)", UUID.randomUUID(), org, open, offering, now, now)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
