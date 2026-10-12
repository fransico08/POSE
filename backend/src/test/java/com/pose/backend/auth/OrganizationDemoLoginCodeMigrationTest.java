package com.pose.backend.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationDemoLoginCodeMigrationTest {
    private static final Map<String, String> DEMO_CODES = Map.of(
            "0b47be39-3b3c-5195-a4e2-8a29ffbadcf0", "edu",
            "47dcbd1f-c1c3-5130-8596-58d76fd2676e", "rea",
            "001ba809-bda6-52d8-8645-5e4ba33533b4", "tech",
            "83236f5d-2b8b-52bb-af64-ccde174487ab", "ret",
            "a477cbf4-afb4-5960-b165-41537f66ac13", "log",
            "68809a13-d597-5c34-a5d8-0d18d9c6cc7a", "cli");

    @Test
    void mapsDemoIdsAndPreservesGeneratedCodeForOtherTenantsOnH2() {
        var dataSource = databaseAtVersionThree();
        var jdbc = new JdbcTemplate(dataSource);
        DEMO_CODES.forEach((id, code) -> insertOrganization(jdbc, id, "org-" + id.replace("-", "")));
        String otherId = UUID.randomUUID().toString();
        String otherCode = "org-" + otherId.replace("-", "");
        insertOrganization(jdbc, otherId, otherCode);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/auth-test-baseline", "classpath:db/migration")
                .load().migrate();
        DEMO_CODES.forEach((id, code) -> assertThat(jdbc.queryForObject(
                "SELECT code FROM organizations WHERE id=?", String.class, UUID.fromString(id))).isEqualTo(code));
        assertThat(jdbc.queryForObject("SELECT code FROM organizations WHERE id=?", String.class, UUID.fromString(otherId)))
                .isEqualTo(otherCode);
    }

    @Test
    void doesNotReplaceAnExplicitOrganizationCode() {
        var dataSource = databaseAtVersionThree();
        var jdbc = new JdbcTemplate(dataSource);
        String id = "0b47be39-3b3c-5195-a4e2-8a29ffbadcf0";
        insertOrganization(jdbc, id, "custom-academy");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/auth-test-baseline", "classpath:db/migration")
                .load().migrate();
        assertThat(jdbc.queryForObject("SELECT code FROM organizations WHERE id=?", String.class, UUID.fromString(id)))
                .isEqualTo("custom-academy");
    }

    private DriverManagerDataSource databaseAtVersionThree() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:codes" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/auth-test-baseline", "classpath:db/migration")
                .target("3").load().migrate();
        return dataSource;
    }

    private void insertOrganization(JdbcTemplate jdbc, String id, String code) {
        jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                UUID.fromString(id), code, "Migration test");
    }
}
