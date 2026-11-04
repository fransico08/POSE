package com.pose.backend.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.crm.CrmJdbc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class AuditQueryRepository {
    private static final String SELECT = "SELECT a.* FROM audit_logs a";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditQueryRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    private AuditEventResponse map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new AuditEventResponse(r.getObject("id", UUID.class), r.getObject("actor_user_id", UUID.class),
                r.getString("actor_type"), r.getString("action"), r.getString("entity_type"),
                r.getObject("entity_id", UUID.class), json(r.getString("before_data")), json(r.getString("after_data")),
                json(r.getString("safe_metadata")), r.getString("result"), CrmJdbc.instant(r, "occurred_at"));
    }

    public long count(String where, List<Object> args) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs a WHERE " + where, Long.class, args.toArray());
    }

    public List<AuditEventResponse> list(String where, List<Object> args, int page, int size) {
        List<Object> values = new ArrayList<>(args); values.add(size); values.add((long) page * size);
        return jdbc.query(SELECT + " WHERE " + where + " ORDER BY a.occurred_at DESC,a.id LIMIT ? OFFSET ?", this::map, values.toArray());
    }

    public Optional<AuditEventResponse> find(String where, List<Object> args, UUID id) {
        List<Object> values = new ArrayList<>(args); values.add(id);
        return jdbc.query(SELECT + " WHERE " + where + " AND a.id=?", this::map, values.toArray()).stream().findFirst();
    }

    private Map<String, Object> json(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }
}
