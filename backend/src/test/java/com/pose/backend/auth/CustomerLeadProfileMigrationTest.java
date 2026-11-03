package com.pose.backend.auth;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import java.sql.Timestamp;
import static org.assertj.core.api.Assertions.*;

class CustomerLeadProfileMigrationTest {
    @Test void migrationPreservesLegacyFieldsAndEnforcesEnumsAndTenantOffering() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:profile"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var config=Flyway.configure().dataSource(ds).locations("classpath:db/auth-test-baseline","classpath:db/migration");
        config.target("8").load().migrate(); var jdbc=new JdbcTemplate(ds);
        Timestamp now=Timestamp.from(new FoundationTestSupport.MutableClock().instant());
        UUID org=FoundationTestSupport.id(501), other=FoundationTestSupport.id(502), user=FoundationTestSupport.id(503), category=FoundationTestSupport.id(504), offering=FoundationTestSupport.id(505), customer=FoundationTestSupport.id(506), lead=FoundationTestSupport.id(507);
        for(UUID id:new UUID[]{org,other}) jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?,'Org',?,?)",id,id.toString(),now,now);
        jdbc.update("INSERT INTO users(id,organization_id,full_name,email,normalized_email,created_at,updated_at) VALUES (?,?,'User','x@x.test','x@x.test',?,?)",user,org,now,now);
        jdbc.update("INSERT INTO offering_categories(id,organization_id,code,name,created_at,updated_at) VALUES (?,?,'CAT','Category',?,?)",category,other,now,now);
        jdbc.update("INSERT INTO offerings(id,organization_id,category_id,code,name,kind,status,created_at,updated_at) VALUES (?,?,?,'OFF','Offer','SERVICE','ACTIVE',?,?)",offering,other,category,now,now);
        jdbc.update("INSERT INTO customers(id,organization_id,owner_user_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,'Customer','a@a.test','a@a.test','ACTIVE',?,?)",customer,org,user,now,now);
        jdbc.update("INSERT INTO leads(id,organization_id,owner_user_id,offering_id,display_name,email,normalized_email,status,created_at,updated_at) VALUES (?,?,?,?,'Lead','a@a.test','a@a.test','NEW',?,?)",lead,org,user,offering,now,now);
        Flyway.configure().dataSource(ds).locations("classpath:db/auth-test-baseline","classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT customer_type FROM customers WHERE id=?",String.class,customer)).isNull();
        assertThat(jdbc.queryForObject("SELECT source FROM leads WHERE id=?",String.class,lead)).isNull();
        assertThatThrownBy(()->jdbc.update("UPDATE customers SET customer_type='WRONG' WHERE id=?",customer)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE leads SET source='WRONG' WHERE id=?",lead)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE customers SET related_offering_id=? WHERE id=?",offering,customer)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
