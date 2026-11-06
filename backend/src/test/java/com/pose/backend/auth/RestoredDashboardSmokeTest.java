package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="POSE_V2_SMOKE_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+")
@SpringBootTest(properties={"pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=","spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Transactional
class RestoredDashboardSmokeTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder passwords;
    private static final Instant START=Instant.parse("2026-08-31T17:00:00Z"), END=Instant.parse("2026-09-30T17:00:00Z");
    private static final Timestamp START_SQL=Timestamp.from(START), END_SQL=Timestamp.from(END);

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("POSE_V2_SMOKE_DATABASE_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_v2_review_[a-z0-9_]+")) throw new IllegalArgumentException("Dedicated restore required");
        properties.add("spring.datasource.url",()->url); properties.add("spring.datasource.username",()->"pose_auth_test"); properties.add("spring.datasource.password",()->"");
        properties.add("spring.flyway.baseline-on-migrate",()->"true"); properties.add("spring.flyway.baseline-version",()->"1");
    }

    @Test void apiMatchesAdjustedW0901ForAllSixOrganizations() throws Exception {
        List<Map<String,Object>> orgs=jdbc.queryForList("SELECT id,code FROM organizations ORDER BY code");
        assertThat(orgs).hasSize(6);
        for(var org:orgs) {
            UUID organization=(UUID)org.get("id"), user=UUID.randomUUID(); String code=org.get("code").toString();
            String email="e2e-dashboard-"+user+"@example.test", password=UUID.randomUUID()+"-test";
            Timestamp now=Timestamp.from(Instant.now().minusSeconds(1));
            jdbc.update("INSERT INTO users(id,organization_id,email,normalized_email,full_name,status,created_at,updated_at) VALUES (?,?,?,?,?,'ACTIVE',?,?)",user,organization,email,email,"Temporary dashboard test",now,now);
            jdbc.update("INSERT INTO credentials(user_id,password_hash,password_changed_at,updated_at) VALUES (?,?,?,?)",user,passwords.encode(password),now,now);
            int grants=jdbc.update("INSERT INTO user_role_grants(id,organization_id,user_id,role_id,granted_at) SELECT ?,?,?,id,? FROM roles WHERE organization_id=? AND code='ADMIN' AND status='ACTIVE'",UUID.randomUUID(),organization,user,now,organization);
            assertThat(grants).isEqualTo(1);
            var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(mapper.writeValueAsString(Map.of("organizationCode",code,"email",email,"password",password)))).andExpect(status().isOk()).andReturn().getResponse();
            String token=mapper.readTree(login.getContentAsString()).get("accessToken").asText();
            int auditBefore=jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE organization_id=?",Integer.class,organization);
            var response=mvc.perform(get("/api/v1/dashboard/kpis").param("start",START.toString()).param("end",END.toString()).param("asOf",END.toString()).param("timezone","Asia/Ho_Chi_Minh").header("Authorization","Bearer "+token))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            JsonNode body=mapper.readTree(response.getContentAsString());
            assertThat(body.get("organizationId").asText()).isEqualTo(organization.toString());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE organization_id=?",Integer.class,organization)).isEqualTo(auditBefore);
            reconcile(body,organization);
            long archivedLeads=scalar("SELECT count(*) FROM leads WHERE organization_id=? AND archived_at IS NOT NULL",organization).longValue();
            long archivedCustomers=scalar("SELECT count(*) FROM customers WHERE organization_id=? AND archived_at IS NOT NULL",organization).longValue();
            long futureInteractions=scalar("SELECT count(*) FROM interactions WHERE organization_id=? AND customer_id IS NOT NULL AND occurred_at>?",organization,END_SQL).longValue();
            long b05Completed=scalar("SELECT count(*) FROM crm_tasks WHERE organization_id=? AND status='DONE' AND completed_at>=? AND completed_at<?",organization,START_SQL,END_SQL).longValue();
            long periodLeads=scalar("SELECT count(*) FROM leads WHERE organization_id=? AND archived_at IS NULL AND created_at>=? AND created_at<?",organization,START_SQL,END_SQL).longValue();
            long periodInteractions=scalar("SELECT count(*) FROM interactions WHERE organization_id=? AND occurred_at>=? AND occurred_at<?",organization,START_SQL,END_SQL).longValue();
            long periodFailures=scalar("SELECT count(*) FROM workflow_executions WHERE organization_id=? AND status='FAILED' AND finished_at>=? AND finished_at<?",organization,START_SQL,END_SQL).longValue();
            if(periodLeads==0) noData(byDimension(body,"A05","kind","NO_RESPONSE"));
            if(periodInteractions==0) { noData(single(body,"D01")); noData(single(body,"D02")); }
            if(periodFailures==0) for(JsonNode metric:body.path("metrics")) if(metric.path("metricId").asText().startsWith("C02")) noData(metric);
            long changedAtCurrent=scalar("""
                    SELECT count(*) FROM (
                      SELECT c.id,max(i.occurred_at) AS original_at,
                        max(i.occurred_at) FILTER (WHERE i.occurred_at<=?) AS bounded_at
                      FROM customers c LEFT JOIN interactions i ON i.organization_id=c.organization_id AND i.customer_id=c.id
                      WHERE c.organization_id=? AND c.archived_at IS NULL GROUP BY c.id
                    ) activity WHERE original_at IS DISTINCT FROM bounded_at
                    """,Timestamp.from(Instant.now()),organization).longValue();
            assertThat(changedAtCurrent).isZero();
            System.out.println("DASHBOARD_RECONCILE code="+code+" metrics=16 matched=16 archived_leads="+archivedLeads+" archived_customers="+archivedCustomers+" b05_completed="+b05Completed+" period_leads="+periodLeads+" period_interactions="+periodInteractions+" period_failures="+periodFailures+" customer_interactions_after_asof="+futureInteractions+" current_a06_changed="+changedAtCurrent);
        }
        UUID sample=(UUID)orgs.getFirst().get("id");
        explain("leads", "EXPLAIN SELECT status,count(*) FROM leads WHERE organization_id=? AND archived_at IS NULL GROUP BY status",sample);
        explain("tasks", "EXPLAIN SELECT status,priority,owner_user_id,count(*) FROM crm_tasks WHERE organization_id=? GROUP BY status,priority,owner_user_id",sample);
        explain("interactions", "EXPLAIN SELECT channel,recorded_by_user_id,count(*) FROM interactions WHERE organization_id=? AND occurred_at>=? AND occurred_at<? GROUP BY channel,recorded_by_user_id",sample,START_SQL,END_SQL);
        explain("workflows", "EXPLAIN SELECT workflow_type,retry_count,count(*) FROM workflow_executions WHERE organization_id=? AND status='FAILED' AND finished_at>=? AND finished_at<? GROUP BY workflow_type,retry_count",sample,START_SQL,END_SQL);
    }

    private void explain(String group,String sql,Object... args) {
        List<String> plan=jdbc.queryForList(sql,String.class,args);
        System.out.println("DASHBOARD_EXPLAIN group="+group+" plan="+String.join(" | ",plan).replaceAll("(?i)\\b[0-9a-f]{8}-[0-9a-f-]{27,}\\b","<organization>"));
    }

    private void reconcile(JsonNode body,UUID org) {
        Map<String,Object> lead=jdbc.queryForMap("""
                SELECT count(*) AS total,count(*) FILTER(WHERE status='QUALIFIED') AS qualified,
                  count(*) FILTER(WHERE converted_customer_id IS NOT NULL) AS converted,
                  count(*) FILTER(WHERE status='QUALIFIED')::numeric/NULLIF(count(*),0) AS qualified_rate,
                  count(*) FILTER(WHERE converted_customer_id IS NOT NULL)::numeric/NULLIF(count(*),0) AS converted_rate
                FROM leads WHERE organization_id=? AND archived_at IS NULL
                """,org);
        same(single(body,"A01"),number(lead.get("total"))); same(single(body,"A03"),number(lead.get("qualified_rate"))); same(single(body,"A04"),number(lead.get("converted_rate")));
        buckets(body,"A02",List.of("status"),"SELECT status AS key,count(*)::numeric AS value FROM leads WHERE organization_id=? AND archived_at IS NULL GROUP BY status",org);
        var response=jdbc.queryForMap("""
                SELECT count(*) AS total,count(first_at) AS responded,avg(extract(epoch FROM first_at-created_at)) AS average
                FROM (SELECT l.id,l.created_at,min(i.occurred_at) FILTER(WHERE i.occurred_at>=l.created_at) AS first_at
                  FROM leads l LEFT JOIN interactions i ON i.organization_id=l.organization_id AND i.lead_id=l.id AND i.occurred_at>=? AND i.occurred_at<?
                  WHERE l.organization_id=? AND l.archived_at IS NULL AND l.created_at>=? AND l.created_at<? GROUP BY l.id,l.created_at) r
                """,START_SQL,END_SQL,org,START_SQL,END_SQL);
        same(byDimension(body,"A05","kind","RESPONDED"),number(response.get("average")));
        BigDecimal total=number(response.get("total")),responded=number(response.get("responded"));
        same(byDimension(body,"A05","kind","NO_RESPONSE"),total.signum()==0?null:total.subtract(responded));
        buckets(body,"A06",List.of("segment"),"""
                SELECT segment AS key,count(*)::numeric AS value FROM (
                  SELECT CASE WHEN max(i.occurred_at) IS NULL THEN 'NEVER_ENGAGED'
                    WHEN max(i.occurred_at)>?::timestamptz-interval '30 days' THEN 'ACTIVE'
                    WHEN max(i.occurred_at)>=?::timestamptz-interval '60 days' THEN 'AT_RISK' ELSE 'INACTIVE' END AS segment
                  FROM customers c LEFT JOIN interactions i ON i.organization_id=c.organization_id AND i.customer_id=c.id AND i.occurred_at<=?
                  WHERE c.organization_id=? AND c.archived_at IS NULL GROUP BY c.id) x GROUP BY segment
                """,END_SQL,END_SQL,END_SQL,org);
        same(single(body,"B01"),scalar("SELECT count(*) FROM crm_tasks WHERE organization_id=? AND status IN ('OPEN','IN_PROGRESS')",org));
        same(single(body,"B02"),scalar("SELECT count(*) FROM crm_tasks WHERE organization_id=? AND status IN ('OPEN','IN_PROGRESS') AND due_at<?",org,END_SQL));
        buckets(body,"B03",List.of("priority","status"),"SELECT priority||'|'||status AS key,count(*)::numeric AS value FROM crm_tasks WHERE organization_id=? GROUP BY priority,status",org);
        buckets(body,"B04",List.of("ownerUserId"),"SELECT owner_user_id::text AS key,count(*)::numeric AS value FROM crm_tasks WHERE organization_id=? GROUP BY owner_user_id",org);
        BigDecimal b05=scalar("SELECT count(*) FILTER(WHERE completed_at<=due_at)::numeric/NULLIF(count(*),0) FROM crm_tasks WHERE organization_id=? AND status='DONE' AND completed_at>=? AND completed_at<?",org,START_SQL,END_SQL);
        same(single(body,"B05"),b05);
        if(b05==null) assertThat(single(body,"B05").path("availability").asText()).isEqualTo("NOT_AVAILABLE_ZERO_DENOMINATOR");
        buckets(body,"C01",List.of("workflowType"),"""
                SELECT scope.workflow_type AS key,count(e.id) FILTER(WHERE e.status='DONE')::numeric/NULLIF(count(e.id),0) AS value
                FROM (SELECT DISTINCT workflow_type,organization_id FROM workflow_executions WHERE organization_id=?) scope
                LEFT JOIN workflow_executions e ON e.organization_id=scope.organization_id AND e.workflow_type=scope.workflow_type
                  AND e.status IN ('DONE','FAILED') AND e.finished_at>=? AND e.finished_at<? GROUP BY scope.workflow_type
                """,org,START_SQL,END_SQL);
        buckets(body,"C02",List.of("workflowType","retryCount"),"SELECT workflow_type||'|'||retry_count AS key,count(*)::numeric AS value FROM workflow_executions WHERE organization_id=? AND status='FAILED' AND finished_at>=? AND finished_at<? GROUP BY workflow_type,retry_count",org,START_SQL,END_SQL);
        buckets(body,"C03",List.of("workflowType"),"""
                SELECT scope.workflow_type AS key,avg(extract(epoch FROM e.finished_at-e.started_at)) FILTER(WHERE e.finished_at>=e.started_at) AS value
                FROM (SELECT DISTINCT workflow_type,organization_id FROM workflow_executions WHERE organization_id=?) scope
                LEFT JOIN workflow_executions e ON e.organization_id=scope.organization_id AND e.workflow_type=scope.workflow_type
                  AND e.status IN ('DONE','FAILED') AND e.finished_at>=? AND e.finished_at<? GROUP BY scope.workflow_type
                """,org,START_SQL,END_SQL);
        buckets(body,"D01",List.of("channel"),"SELECT COALESCE(NULLIF(channel,''),'UNKNOWN') AS key,count(*)::numeric AS value FROM interactions WHERE organization_id=? AND occurred_at>=? AND occurred_at<? GROUP BY COALESCE(NULLIF(channel,''),'UNKNOWN')",org,START_SQL,END_SQL);
        buckets(body,"D02",List.of("userId"),"SELECT recorded_by_user_id::text AS key,count(*)::numeric AS value FROM interactions WHERE organization_id=? AND occurred_at>=? AND occurred_at<? GROUP BY recorded_by_user_id",org,START_SQL,END_SQL);
    }
    private BigDecimal scalar(String sql,Object... args) {return jdbc.queryForObject(sql,BigDecimal.class,args);}
    private static BigDecimal number(Object value) {return value==null?null:new BigDecimal(value.toString());}
    private static BigDecimal value(JsonNode node) {return node==null||node.isNull()?null:node.decimalValue();}
    private static JsonNode single(JsonNode body,String prefix) {for(JsonNode m:body.get("metrics"))if(m.get("metricId").asText().startsWith(prefix))return m;throw new AssertionError("Missing "+prefix);}
    private static JsonNode byDimension(JsonNode body,String prefix,String dimension,String key) {for(JsonNode m:body.get("metrics"))if(m.get("metricId").asText().startsWith(prefix)&&m.path("dimensions").path(dimension).asText().equals(key))return m;throw new AssertionError("Missing "+prefix+" bucket");}
    private static void same(JsonNode metric,BigDecimal expected) {BigDecimal actual=value(metric.get("value"));if(expected==null)assertThat(actual).isNull();else assertThat(actual).isNotNull().isEqualByComparingTo(expected);}
    private static void noData(JsonNode metric) {assertThat(metric.path("value").isNull()).isTrue();assertThat(metric.path("availability").asText()).isEqualTo("NO_DATA_IN_PERIOD");}
    private void buckets(JsonNode body,String prefix,List<String> dimensions,String sql,Object... args) {
        Map<String,BigDecimal> expected=new TreeMap<>(), actual=new TreeMap<>();
        for(var row:jdbc.queryForList(sql,args)) expected.put(row.get("key").toString(),number(row.get("value")));
        for(JsonNode m:body.get("metrics")) if(m.get("metricId").asText().startsWith(prefix)) {
            List<String> parts=new ArrayList<>();for(String dimension:dimensions) if(m.path("dimensions").has(dimension)) parts.add(m.path("dimensions").get(dimension).asText());
            if(parts.size()==dimensions.size() && !(prefix.equals("C02") && m.get("value").isNull())) actual.put(String.join("|",parts),value(m.get("value")));
        }
        assertThat(actual.keySet()).as(prefix).isEqualTo(expected.keySet());
        expected.forEach((key,expectedValue)->{BigDecimal actualValue=actual.get(key);if(expectedValue==null)assertThat(actualValue).as(prefix).isNull();else assertThat(actualValue).as(prefix).isNotNull().isEqualByComparingTo(expectedValue);});
    }
}
