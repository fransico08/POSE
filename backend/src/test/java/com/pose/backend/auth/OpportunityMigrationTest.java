package com.pose.backend.auth;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpportunityMigrationTest {
    @Test
    void v10PreservesLegacyForecastWithoutInventingUnitPrice() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:opportunity-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE public.offerings (id UUID PRIMARY KEY, organization_id UUID NOT NULL, UNIQUE (organization_id,id))");
        jdbc.execute("CREATE TABLE public.opportunities (id UUID PRIMARY KEY, organization_id UUID NOT NULL, offering_id UUID NOT NULL, expected_value NUMERIC(18,2), stage VARCHAR(30) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");

        UUID organizationId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID pricedOpportunity = UUID.randomUUID();
        UUID unpricedOpportunity = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-01T00:00:00Z");
        jdbc.update("INSERT INTO public.offerings(id,organization_id) VALUES (?,?)", offeringId, organizationId);
        jdbc.update("INSERT INTO public.opportunities(id,organization_id,offering_id,expected_value,stage,created_at,updated_at) VALUES (?,?,?,?,'NEW',?,?),(?,?,?,NULL,'NEW',?,?)",
                pricedOpportunity, organizationId, offeringId, new BigDecimal("1250000.50"), Timestamp.from(createdAt), Timestamp.from(createdAt),
                unpricedOpportunity, organizationId, offeringId, Timestamp.from(createdAt), Timestamp.from(createdAt));

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__opportunity_pipeline_lines.sql")).execute(dataSource);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.opportunity_lines", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT line_value FROM public.opportunity_lines WHERE opportunity_id=?", BigDecimal.class, pricedOpportunity))
                .isEqualByComparingTo("1250000.50");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM public.opportunity_lines WHERE opportunity_id=?", BigDecimal.class, pricedOpportunity)).isNull();
        assertThat(jdbc.queryForObject("SELECT legacy_value_only FROM public.opportunity_lines WHERE opportunity_id=?", Boolean.class, pricedOpportunity)).isTrue();
        assertThat(jdbc.queryForObject("SELECT line_value FROM public.opportunity_lines WHERE opportunity_id=?", BigDecimal.class, unpricedOpportunity)).isNull();
        assertThat(jdbc.queryForObject("SELECT expected_value FROM public.opportunities WHERE id=?", BigDecimal.class, pricedOpportunity))
                .isEqualByComparingTo("1250000.50");
    }
}
