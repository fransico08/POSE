package com.pose.backend.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Grouped, tenant-scoped facts; the supplied predicates are constructed by the authorization service. */
@Repository
public class DashboardRepository {
    public record Scope(String where, List<Object> args) {
        public Scope { args = List.copyOf(args); }
    }

    private final JdbcTemplate jdbc;
    public DashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> leadSummary(Scope scope) {
        return jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(CASE WHEN l.status='QUALIFIED' THEN 1 END) AS qualified,
                       count(CASE WHEN l.converted_customer_id IS NOT NULL THEN 1 END) AS converted,
                       CAST(count(CASE WHEN l.status='QUALIFIED' THEN 1 END) AS NUMERIC) / NULLIF(count(*),0) AS qualified_rate,
                       CAST(count(CASE WHEN l.converted_customer_id IS NOT NULL THEN 1 END) AS NUMERIC) / NULLIF(count(*),0) AS converted_rate
                FROM leads l WHERE
                """ + scope.where(), scope.args().toArray());
    }

    public List<Map<String, Object>> leadStatuses(Scope scope) {
        return jdbc.queryForList("SELECT l.status, count(*) AS total FROM leads l WHERE " + scope.where()
                + " GROUP BY l.status ORDER BY l.status", scope.args().toArray());
    }

    public Map<String, Object> firstResponse(Scope leads, Scope interactions, Instant start, Instant end) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(start)); args.add(Timestamp.from(end)); args.addAll(interactions.args());
        args.addAll(leads.args()); args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        return jdbc.queryForMap("""
                SELECT count(*) AS total, count(first_at) AS responded,
                       avg(EXTRACT(EPOCH FROM (first_at-created_at))) AS avg_seconds
                FROM (
                  SELECT l.id, l.created_at, min(i.occurred_at) AS first_at
                  FROM leads l LEFT JOIN interactions i ON i.organization_id=l.organization_id AND i.lead_id=l.id
                    AND i.occurred_at>=? AND i.occurred_at<? AND i.occurred_at>=l.created_at AND (
                """ + interactions.where() + ") WHERE " + leads.where() + """
                    AND l.created_at>=? AND l.created_at<? GROUP BY l.id,l.created_at
                ) response_times
                """, args.toArray());
    }

    public List<Map<String, Object>> engagement(Scope customers, Scope interactions, Instant asOf, boolean excludeFuture) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(asOf.minusSeconds(30L * 86400))); args.add(Timestamp.from(asOf.minusSeconds(60L * 86400)));
        args.addAll(interactions.args());
        if (excludeFuture) args.add(Timestamp.from(asOf));
        args.addAll(customers.args());
        return jdbc.queryForList("""
                SELECT segment,count(*) AS total FROM (
                  SELECT CASE WHEN last_at IS NULL THEN 'NEVER_ENGAGED'
                    WHEN last_at>? THEN 'ACTIVE' WHEN last_at>=? THEN 'AT_RISK' ELSE 'INACTIVE' END AS segment
                  FROM (SELECT c.id,max(i.occurred_at) AS last_at FROM customers c
                    LEFT JOIN interactions i ON i.organization_id=c.organization_id AND i.customer_id=c.id AND (
                """ + interactions.where() + ")" + (excludeFuture ? " AND i.occurred_at<=?" : "") + " WHERE " + customers.where() + """
                     GROUP BY c.id) customer_activity
                ) segmented GROUP BY segment ORDER BY segment
                """, args.toArray());
    }

    public List<Map<String, Object>> taskBuckets(Scope scope) {
        return jdbc.queryForList("""
                SELECT t.status,t.priority,t.owner_user_id,count(*) AS total
                FROM crm_tasks t WHERE
                """ + scope.where()
                + " GROUP BY t.status,t.priority,t.owner_user_id ORDER BY t.status,t.priority,t.owner_user_id", scope.args().toArray());
    }

    public Map<String, Object> taskSummary(Scope scope, Instant asOf, Instant start, Instant end) {
        List<Object> args = new ArrayList<>();
        args.add(Timestamp.from(asOf)); args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        args.add(Timestamp.from(start)); args.add(Timestamp.from(end)); args.addAll(scope.args());
        return jdbc.queryForMap("""
                SELECT count(CASE WHEN t.status IN ('OPEN','IN_PROGRESS') THEN 1 END) AS open_count,
                       count(CASE WHEN t.status IN ('OPEN','IN_PROGRESS') AND t.due_at<? THEN 1 END) AS overdue_count,
                       count(CASE WHEN t.status='DONE' AND t.completed_at>=? AND t.completed_at<? THEN 1 END) AS completed_count,
                       count(CASE WHEN t.status='DONE' AND t.completed_at>=? AND t.completed_at<? AND t.completed_at<=t.due_at THEN 1 END) AS on_time_count,
                       CAST(count(CASE WHEN t.status='DONE' AND t.completed_at>=? AND t.completed_at<? AND t.completed_at<=t.due_at THEN 1 END) AS NUMERIC)
                         / NULLIF(count(CASE WHEN t.status='DONE' AND t.completed_at>=? AND t.completed_at<? THEN 1 END),0) AS on_time_rate
                FROM crm_tasks t WHERE
                """ + scope.where(), args.toArray());
    }

    public List<Map<String, Object>> interactionBuckets(Scope scope, Instant start, Instant end) {
        List<Object> args = new ArrayList<>(scope.args()); args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        return jdbc.queryForList("""
                SELECT COALESCE(NULLIF(i.channel,''),'UNKNOWN') AS channel, i.recorded_by_user_id, count(*) AS total
                FROM interactions i WHERE
                """ + scope.where() + """
                 AND i.occurred_at>=? AND i.occurred_at<?
                 GROUP BY COALESCE(NULLIF(i.channel,''),'UNKNOWN'),i.recorded_by_user_id
                 ORDER BY channel,i.recorded_by_user_id
                """, args.toArray());
    }

    public List<Map<String, Object>> workflowSummary(Scope scope, Instant start, Instant end) {
        List<Object> args = new ArrayList<>(scope.args()); args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        return jdbc.queryForList("""
                WITH workflow_types AS (SELECT DISTINCT w.workflow_type,w.organization_id FROM workflow_executions w WHERE
                """ + scope.where() + """
                ) SELECT s.workflow_type,
                    count(CASE WHEN e.status='DONE' THEN 1 END) AS done_count,
                    count(CASE WHEN e.status='FAILED' THEN 1 END) AS failed_count,
                    CAST(count(CASE WHEN e.status='DONE' THEN 1 END) AS NUMERIC) / NULLIF(count(e.id),0) AS success_rate,
                    count(CASE WHEN e.finished_at>=e.started_at THEN 1 END) AS valid_count,
                    avg(CASE WHEN e.finished_at>=e.started_at THEN EXTRACT(EPOCH FROM (e.finished_at-e.started_at)) END) AS avg_seconds
                FROM workflow_types s LEFT JOIN workflow_executions e ON e.organization_id=s.organization_id
                    AND e.workflow_type=s.workflow_type AND e.finished_at>=? AND e.finished_at<? AND e.status IN ('DONE','FAILED')
                GROUP BY s.workflow_type ORDER BY s.workflow_type
                """, args.toArray());
    }

    public List<Map<String, Object>> failedWorkflowBuckets(Scope scope, Instant start, Instant end) {
        List<Object> args = new ArrayList<>(scope.args()); args.add(Timestamp.from(start)); args.add(Timestamp.from(end));
        return jdbc.queryForList("""
                SELECT w.workflow_type,w.retry_count,count(*) AS total FROM workflow_executions w WHERE
                """ + scope.where() + """
                 AND w.status='FAILED' AND w.finished_at>=? AND w.finished_at<?
                 GROUP BY w.workflow_type,w.retry_count ORDER BY w.workflow_type,w.retry_count
                """, args.toArray());
    }
}
