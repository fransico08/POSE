package com.pose.backend.task;

import com.pose.backend.crm.CrmJdbc;
import com.pose.backend.crm.CrmWorkReferences;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class TaskRepository {
    private static final String FROM = " FROM crm_tasks t JOIN users u ON u.id=t.owner_user_id AND u.organization_id=t.organization_id "
            + "LEFT JOIN customers wc ON wc.id=t.customer_id AND wc.organization_id=t.organization_id "
            + "LEFT JOIN leads wl ON wl.id=t.lead_id AND wl.organization_id=t.organization_id "
            + "LEFT JOIN opportunities wo ON wo.id=t.opportunity_id AND wo.organization_id=t.organization_id ";
    private static final String SELECT = "SELECT t.*,u.full_name AS owner_name,wc.id AS work_customer_id,wc.display_name AS work_customer_name,"
            + "wl.id AS work_lead_id,wl.display_name AS work_lead_name,wo.id AS work_opportunity_id,wo.title AS work_opportunity_title";
    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static TaskResponse map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new TaskResponse(r.getObject("id", UUID.class), r.getObject("customer_id", UUID.class),
                r.getObject("lead_id", UUID.class), r.getObject("opportunity_id", UUID.class),
                r.getObject("owner_user_id", UUID.class), r.getString("owner_name"),
                r.getObject("created_by_user_id", UUID.class), r.getString("title"), r.getString("description"),
                r.getString("priority"), r.getString("status"), CrmJdbc.instant(r, "due_at"), r.getString("result"),
                r.getString("cancellation_reason"), r.getString("origin"), CrmJdbc.instant(r, "completed_at"),
                CrmJdbc.instant(r, "created_at"), CrmJdbc.instant(r, "updated_at"),
                CrmWorkReferences.customer(r), CrmWorkReferences.lead(r), CrmWorkReferences.opportunity(r));
    }

    public long count(String where, List<Object> args) {
        return jdbc.queryForObject("SELECT count(*)" + FROM + " WHERE " + where, Long.class, args.toArray());
    }

    public List<TaskResponse> list(String where, List<Object> args, int page, int size) {
        List<Object> values = new ArrayList<>(args); values.add(size); values.add((long) page * size);
        return jdbc.query(SELECT + FROM + " WHERE " + where + " ORDER BY t.due_at,t.id LIMIT ? OFFSET ?", TaskRepository::map, values.toArray());
    }

    public Optional<TaskResponse> find(String where, List<Object> args, UUID id) {
        List<Object> values = new ArrayList<>(args); values.add(id);
        return jdbc.query(SELECT + FROM + " WHERE " + where + " AND t.id=?", TaskRepository::map, values.toArray()).stream().findFirst();
    }

    public void insert(UUID id, UUID organizationId, UUID customerId, UUID leadId, UUID opportunityId,
                       UUID ownerId, UUID createdBy, String title, String description, String priority,
                       Instant dueAt, Instant now) {
        jdbc.update("""
                INSERT INTO crm_tasks(id,organization_id,customer_id,lead_id,opportunity_id,owner_user_id,
                    created_by_user_id,title,description,priority,status,due_at,origin,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,'OPEN',?,'MANUAL',?,?)
                """, id, organizationId, customerId, leadId, opportunityId, ownerId, createdBy, title,
                description, priority, Timestamp.from(dueAt), Timestamp.from(now), Timestamp.from(now));
    }

    public void update(UUID organizationId, UUID id, String title, String description, String priority,
                       Instant dueAt, Instant now) {
        jdbc.update("""
                UPDATE crm_tasks SET title=?,description=?,priority=?,due_at=?,updated_at=?
                WHERE organization_id=? AND id=?
                """, title, description, priority, Timestamp.from(dueAt), Timestamp.from(now), organizationId, id);
    }

    public void assign(UUID organizationId, UUID id, UUID ownerId, Instant now) {
        jdbc.update("UPDATE crm_tasks SET owner_user_id=?,updated_at=? WHERE organization_id=? AND id=?",
                ownerId, Timestamp.from(now), organizationId, id);
    }

    public void transition(UUID organizationId, UUID id, String status, String result, String reason, Instant completedAt, Instant now) {
        jdbc.update("""
                UPDATE crm_tasks SET status=?,result=?,cancellation_reason=?,completed_at=?,updated_at=?
                WHERE organization_id=? AND id=?
                """, status, result, reason, completedAt == null ? null : Timestamp.from(completedAt),
                Timestamp.from(now), organizationId, id);
    }
}
