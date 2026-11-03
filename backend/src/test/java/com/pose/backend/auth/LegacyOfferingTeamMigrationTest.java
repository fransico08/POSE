package com.pose.backend.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class LegacyOfferingTeamMigrationTest {
    @Test
    void assignsOnlyLegacyOfferingsOfTenantsWithActiveSalesTeam() {
        var dataSource = databaseAtVersionSeven();
        var jdbc = new JdbcTemplate(dataSource);
        UUID withSales = organization(jdbc, "with-sales");
        UUID salesTeam = team(jdbc, withSales, "SALES", "ACTIVE");
        UUID careTeam = team(jdbc, withSales, "CARE_DATA", "ACTIVE");
        UUID legacy = offering(jdbc, withSales, null);
        UUID alreadyOwned = offering(jdbc, withSales, careTeam);
        UUID inactiveSales = organization(jdbc, "inactive-sales");
        team(jdbc, inactiveSales, "SALES", "INACTIVE");
        UUID inactiveLegacy = offering(jdbc, inactiveSales, null);
        UUID noSales = organization(jdbc, "no-sales");
        team(jdbc, noSales, "CARE_DATA", "ACTIVE");
        UUID noSalesLegacy = offering(jdbc, noSales, null);

        migrate(dataSource, null);

        assertThat(teamOf(jdbc, legacy)).isEqualTo(salesTeam);
        assertThat(teamOf(jdbc, alreadyOwned)).isEqualTo(careTeam);
        assertThat(teamOf(jdbc, inactiveLegacy)).isNull();
        assertThat(teamOf(jdbc, noSalesLegacy)).isNull();
    }

    private DriverManagerDataSource databaseAtVersionSeven() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:legacy" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        migrate(dataSource, "7");
        return dataSource;
    }

    private void migrate(DriverManagerDataSource dataSource, String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/auth-test-baseline", "classpath:db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }

    private UUID organization(JdbcTemplate jdbc, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, code, "Migration test");
        jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, id, "Manager", code + "@example.com", code + "@example.com");
        jdbc.update("INSERT INTO departments(id,organization_id,code,name,created_at) VALUES (?,?,'SALES','Sales',CURRENT_TIMESTAMP)", id, id);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,created_at,updated_at) VALUES (?,?,'CAT','Category',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, id);
        return id;
    }

    private UUID team(JdbcTemplate jdbc, UUID organization, String code, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO teams(id,organization_id,code,name,manager_user_id,department_id,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, organization, code, code, organization, organization, status);
        return id;
    }

    private UUID offering(JdbcTemplate jdbc, UUID organization, UUID team) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,?,?,?,'SERVICE','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, organization, organization, team, "OFF-" + id, "Offering");
        return id;
    }

    private UUID teamOf(JdbcTemplate jdbc, UUID offering) {
        return jdbc.queryForObject("SELECT team_id FROM offerings WHERE id=?", UUID.class, offering);
    }
}
