package com.pose.backend.interaction;

import com.pose.backend.crm.CrmJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class InteractionRepository {
    private static final String FROM = " FROM interactions i JOIN users u ON u.id=i.recorded_by_user_id AND u.organization_id=i.organization_id ";
    private static final String SELECT = "SELECT i.*,u.full_name AS recorded_by_name";
    private final JdbcTemplate jdbc;

    public InteractionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static InteractionResponse map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new InteractionResponse(r.getObject("id", UUID.class), r.getObject("customer_id", UUID.class),
                r.getObject("lead_id", UUID.class), r.getObject("opportunity_id", UUID.class),
                r.getObject("recorded_by_user_id", UUID.class), r.getString("recorded_by_name"),
                r.getString("type"), r.getString("channel"), r.getString("subject"), r.getString("content"),
                r.getString("outcome"), r.getString("visibility"), CrmJdbc.instant(r, "occurred_at"),
                CrmJdbc.instant(r, "created_at"), CrmJdbc.instant(r, "updated_at"));
    }

    public long count(String where, List<Object> args) {
        return jdbc.queryForObject("SELECT count(*)" + FROM + " WHERE " + where, Long.class, args.toArray());
    }

    public List<InteractionResponse> list(String where, List<Object> args, int page, int size) {
        List<Object> values = new ArrayList<>(args); values.add(size); values.add((long) page * size);
        return jdbc.query(SELECT + FROM + " WHERE " + where + " ORDER BY i.occurred_at DESC,i.id LIMIT ? OFFSET ?", InteractionRepository::map, values.toArray());
    }

    public Optional<InteractionResponse> find(String where, List<Object> args, UUID id) {
        List<Object> values = new ArrayList<>(args); values.add(id);
        return jdbc.query(SELECT + FROM + " WHERE " + where + " AND i.id=?", InteractionRepository::map, values.toArray()).stream().findFirst();
    }

    public void insert(UUID id, UUID organizationId, UUID customerId, UUID leadId, UUID opportunityId,
                       UUID actorId, String type, String channel, String subject, String content,
                       String outcome, String visibility, Instant occurredAt, Instant now) {
        jdbc.update("""
                INSERT INTO interactions(id,organization_id,customer_id,lead_id,opportunity_id,recorded_by_user_id,
                    type,channel,subject,content,outcome,visibility,occurred_at,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, organizationId, customerId, leadId, opportunityId, actorId, type, channel, subject,
                content, outcome, visibility, Timestamp.from(occurredAt), Timestamp.from(now), Timestamp.from(now));
    }

    public void update(UUID organizationId, UUID id, String type, String channel, String subject, String content,
                       String outcome, String visibility, Instant occurredAt, Instant now) {
        jdbc.update("""
                UPDATE interactions SET type=?,channel=?,subject=?,content=?,outcome=?,visibility=?,occurred_at=?,updated_at=?
                WHERE organization_id=? AND id=?
                """, type, channel, subject, content, outcome, visibility, Timestamp.from(occurredAt),
                Timestamp.from(now), organizationId, id);
    }
}
