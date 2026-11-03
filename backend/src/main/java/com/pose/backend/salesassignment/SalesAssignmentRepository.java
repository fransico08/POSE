package com.pose.backend.salesassignment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SalesAssignmentRepository {
    private static final String SELECT_ASSIGNMENT = """
            SELECT sa.id, sa.organization_id, sa.offering_id, o.code AS offering_code,
                   o.name AS offering_name, o.team_id, t.name AS team_name, t.status AS team_status,
                   sa.sales_user_id, u.full_name AS sales_user_name, sa.assigned_by_user_id,
                   sa.status, sa.effective_from, sa.effective_to, sa.revoked_at,
                   sa.created_at, sa.updated_at
            FROM sales_assignments sa
            JOIN offerings o ON o.id=sa.offering_id AND o.organization_id=sa.organization_id
            LEFT JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
            JOIN users u ON u.id=sa.sales_user_id AND u.organization_id=sa.organization_id
            """;

    private static final RowMapper<StoredAssignment> ASSIGNMENT_MAPPER = (rs, row) -> new StoredAssignment(
            rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
            rs.getObject("offering_id", UUID.class), rs.getString("offering_code"),
            rs.getString("offering_name"), rs.getObject("team_id", UUID.class),
            rs.getString("team_name"), rs.getString("team_status"),
            rs.getObject("sales_user_id", UUID.class), rs.getString("sales_user_name"),
            rs.getObject("assigned_by_user_id", UUID.class), rs.getString("status"),
            rs.getTimestamp("effective_from").toInstant(), instant(rs.getTimestamp("effective_to")),
            instant(rs.getTimestamp("revoked_at")), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public SalesAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredAssignment> find(UUID organizationId, UUID id) {
        return jdbc.query(SELECT_ASSIGNMENT + " WHERE sa.organization_id=? AND sa.id=?",
                ASSIGNMENT_MAPPER, organizationId, id).stream().findFirst();
    }

    public long count(String where, List<Object> args) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM sales_assignments sa
                JOIN offerings o ON o.id=sa.offering_id AND o.organization_id=sa.organization_id
                JOIN users u ON u.id=sa.sales_user_id AND u.organization_id=sa.organization_id
                LEFT JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
                """ + " WHERE " + where, Long.class, args.toArray());
    }

    public List<StoredAssignment> list(String where, List<Object> args, int page, int size) {
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((long) page * size);
        return jdbc.query(SELECT_ASSIGNMENT + " WHERE " + where
                        + " ORDER BY sa.updated_at DESC, sa.created_at DESC, sa.id LIMIT ? OFFSET ?",
                ASSIGNMENT_MAPPER, queryArgs.toArray());
    }

    public Optional<OfferingScope> findOffering(UUID organizationId, UUID offeringId) {
        return jdbc.query("""
                SELECT o.id, o.status AS offering_status, o.team_id, t.status AS team_status
                FROM offerings o
                LEFT JOIN teams t ON t.id=o.team_id AND t.organization_id=o.organization_id
                WHERE o.organization_id=? AND o.id=?
                """, (rs, row) -> new OfferingScope(rs.getObject("id", UUID.class),
                rs.getString("offering_status"), rs.getObject("team_id", UUID.class),
                rs.getString("team_status")), organizationId, offeringId).stream().findFirst();
    }

    public List<SalesAssignmentCandidateResponse> candidates(UUID organizationId, UUID teamId, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        return jdbc.query("""
                SELECT DISTINCT u.id, u.full_name, lower(u.full_name) AS name_sort
                FROM users u
                JOIN team_memberships tm ON tm.user_id=u.id AND tm.organization_id=u.organization_id
                JOIN teams t ON t.id=tm.team_id AND t.organization_id=tm.organization_id
                WHERE u.organization_id=? AND u.status='ACTIVE' AND u.archived_at IS NULL
                  AND tm.team_id=? AND tm.status='ACTIVE' AND tm.joined_at<=?
                  AND (tm.left_at IS NULL OR tm.left_at>?) AND t.status='ACTIVE'
                  AND EXISTS (
                    SELECT 1 FROM user_role_grants g
                    JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                    WHERE g.organization_id=u.organization_id AND g.user_id=u.id
                      AND r.code='SALES_REP' AND r.status='ACTIVE' AND g.granted_at<=?
                      AND (g.revoked_at IS NULL OR g.revoked_at>?)
                  )
                  AND EXISTS (
                    SELECT 1 FROM user_role_grants g
                    JOIN roles r ON r.id=g.role_id AND r.organization_id=g.organization_id
                    JOIN role_permissions rp ON rp.role_id=r.id
                    JOIN permissions p ON p.id=rp.permission_id
                    WHERE g.organization_id=u.organization_id AND g.user_id=u.id
                      AND r.status='ACTIVE' AND p.code='offering.read' AND g.granted_at<=?
                      AND (g.revoked_at IS NULL OR g.revoked_at>?)
                  )
                ORDER BY name_sort, u.id
                """, (rs, row) -> new SalesAssignmentCandidateResponse(
                rs.getObject("id", UUID.class), rs.getString("full_name")),
                organizationId, teamId, timestamp, timestamp, timestamp, timestamp, timestamp, timestamp);
    }

    public boolean isActiveTeamMember(UUID organizationId, UUID teamId, UUID userId, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM team_memberships tm JOIN teams t ON t.id=tm.team_id
                WHERE tm.organization_id=? AND t.organization_id=tm.organization_id AND tm.team_id=?
                  AND tm.user_id=? AND tm.status='ACTIVE' AND t.status='ACTIVE' AND tm.joined_at<=?
                  AND (tm.left_at IS NULL OR tm.left_at>?)
                """, Integer.class, organizationId, teamId, userId, timestamp, timestamp);
        return count != null && count > 0;
    }

    public boolean hasOverlappingAssignment(UUID organizationId, UUID offeringId, UUID salesUserId,
                                            Instant from, Instant to, UUID excludeId) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) FROM sales_assignments
                WHERE organization_id=? AND offering_id=? AND sales_user_id=?
                  AND status='ACTIVE' AND revoked_at IS NULL
                  AND (effective_to IS NULL OR effective_to>=?)
                """);
        List<Object> args = new ArrayList<>(List.of(organizationId, offeringId, salesUserId, Timestamp.from(from)));
        if (to != null) {
            sql.append(" AND effective_from<=?");
            args.add(Timestamp.from(to));
        }
        if (excludeId != null) {
            sql.append(" AND id<>?");
            args.add(excludeId);
        }
        return jdbc.queryForObject(sql.toString(), Long.class, args.toArray()) > 0;
    }

    public void insert(UUID id, UUID organizationId, UUID offeringId, UUID salesUserId,
                       UUID assignedByUserId, Instant effectiveFrom, Instant effectiveTo, Instant now) {
        jdbc.update("""
                INSERT INTO sales_assignments(id,organization_id,offering_id,sales_user_id,assigned_by_user_id,
                    status,effective_from,effective_to,revoked_at,revoke_reason,created_at,updated_at)
                VALUES (?,?,?,?,?,'ACTIVE',?,?,NULL,NULL,?,?)
                """, id, organizationId, offeringId, salesUserId, assignedByUserId,
                Timestamp.from(effectiveFrom), timestamp(effectiveTo), Timestamp.from(now), Timestamp.from(now));
    }

    public void extend(UUID organizationId, UUID id, Instant effectiveTo, Instant now) {
        jdbc.update("""
                UPDATE sales_assignments SET effective_to=?,status='ACTIVE',updated_at=?
                WHERE organization_id=? AND id=? AND status<>'REVOKED' AND revoked_at IS NULL
                """, timestamp(effectiveTo), Timestamp.from(now), organizationId, id);
    }

    public void revoke(UUID organizationId, UUID id, Instant now) {
        jdbc.update("""
                UPDATE sales_assignments SET status='REVOKED',revoked_at=?,updated_at=?
                WHERE organization_id=? AND id=? AND status<>'REVOKED' AND revoked_at IS NULL
                """, Timestamp.from(now), Timestamp.from(now), organizationId, id);
    }

    public long countOpenWork(StoredAssignment assignment) {
        UUID organizationId = assignment.organizationId();
        UUID salesUserId = assignment.salesUserId();
        UUID offeringId = assignment.offeringId();
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM leads l WHERE l.organization_id=? AND l.owner_user_id=?
                    AND l.offering_id=? AND l.archived_at IS NULL
                    AND l.status NOT IN ('CONVERTED','DISQUALIFIED'))
                + (SELECT count(*) FROM opportunities o WHERE o.organization_id=? AND o.owner_user_id=?
                    AND o.offering_id=? AND o.stage NOT IN ('WON','LOST','CANCELLED'))
                + (SELECT count(*) FROM crm_tasks task WHERE task.organization_id=? AND task.owner_user_id=?
                    AND task.status IN ('OPEN','IN_PROGRESS') AND (
                      EXISTS (SELECT 1 FROM leads l WHERE l.id=task.lead_id
                        AND l.organization_id=task.organization_id AND l.offering_id=?)
                      OR EXISTS (SELECT 1 FROM opportunities o WHERE o.id=task.opportunity_id
                        AND o.organization_id=task.organization_id AND o.offering_id=?)
                    ))
                """, Long.class,
                organizationId, salesUserId, offeringId,
                organizationId, salesUserId, offeringId,
                organizationId, salesUserId, offeringId, offeringId);
    }

    public List<SalesAssignmentOpenWorkResponse.Item> openWork(StoredAssignment assignment) {
        return jdbc.query("""
                SELECT * FROM (
                  SELECT 'LEAD' AS record_type, l.id, l.display_name AS title, l.status
                  FROM leads l WHERE l.organization_id=? AND l.owner_user_id=? AND l.offering_id=?
                    AND l.archived_at IS NULL AND l.status NOT IN ('CONVERTED','DISQUALIFIED')
                  UNION ALL
                  SELECT 'OPPORTUNITY' AS record_type, o.id, o.title, o.stage AS status
                  FROM opportunities o WHERE o.organization_id=? AND o.owner_user_id=? AND o.offering_id=?
                    AND o.stage NOT IN ('WON','LOST','CANCELLED')
                  UNION ALL
                  SELECT 'TASK' AS record_type, task.id, task.title, task.status
                  FROM crm_tasks task WHERE task.organization_id=? AND task.owner_user_id=?
                    AND task.status IN ('OPEN','IN_PROGRESS') AND (
                      EXISTS (SELECT 1 FROM leads l WHERE l.id=task.lead_id
                        AND l.organization_id=task.organization_id AND l.offering_id=?)
                      OR EXISTS (SELECT 1 FROM opportunities o WHERE o.id=task.opportunity_id
                        AND o.organization_id=task.organization_id AND o.offering_id=?)
                    )
                ) work ORDER BY record_type,title,id LIMIT 50
                """, (rs, row) -> new SalesAssignmentOpenWorkResponse.Item(
                rs.getString("record_type"), rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("status")),
                assignment.organizationId(), assignment.salesUserId(), assignment.offeringId(),
                assignment.organizationId(), assignment.salesUserId(), assignment.offeringId(),
                assignment.organizationId(), assignment.salesUserId(), assignment.offeringId(), assignment.offeringId());
    }

    public List<StoredAssignment> expiredBatch(Instant now, int limit) {
        return jdbc.query(SELECT_ASSIGNMENT + " WHERE sa.status='ACTIVE' AND sa.revoked_at IS NULL"
                        + " AND sa.effective_to IS NOT NULL AND sa.effective_to<?"
                        + " ORDER BY sa.effective_to,sa.id LIMIT ?",
                ASSIGNMENT_MAPPER, Timestamp.from(now), limit);
    }

    public int markExpired(UUID organizationId, UUID id, Instant now) {
        return jdbc.update("""
                UPDATE sales_assignments SET status='EXPIRED',updated_at=?
                WHERE organization_id=? AND id=? AND status='ACTIVE' AND revoked_at IS NULL
                  AND effective_to IS NOT NULL AND effective_to<?
                """, Timestamp.from(now), organizationId, id, Timestamp.from(now));
    }

    public record OfferingScope(UUID id, String status, UUID teamId, String teamStatus) {
    }

    public record StoredAssignment(UUID id, UUID organizationId, UUID offeringId, String offeringCode,
                                   String offeringName, UUID teamId, String teamName, String teamStatus,
                                   UUID salesUserId, String salesUserName, UUID assignedByUserId, String status,
                                   Instant effectiveFrom, Instant effectiveTo, Instant revokedAt,
                                   Instant createdAt, Instant updatedAt) {
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
