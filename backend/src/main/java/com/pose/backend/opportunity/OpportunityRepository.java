package com.pose.backend.opportunity;

import com.pose.backend.identity.FoundationAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Repository
public class OpportunityRepository {
    private static final String FROM = """
            FROM opportunities o
            LEFT JOIN customers c ON c.id=o.customer_id AND c.organization_id=o.organization_id
            LEFT JOIN leads l ON l.id=o.lead_id AND l.organization_id=o.organization_id
            JOIN users u ON u.id=o.owner_user_id AND u.organization_id=o.organization_id
            """;
    private static final String SELECT = """
            SELECT o.id,o.organization_id,o.customer_id,c.display_name AS customer_name,
                   o.lead_id,l.display_name AS lead_name,o.owner_user_id,u.full_name AS owner_name,
                   o.title,o.stage,o.expected_close_date,o.expected_value,o.currency_code,
                   o.actual_value,o.actual_close_date,o.notes,o.closing_reason,o.closed_at,
                   o.created_at,o.updated_at
            """;
    private static final RowMapper<StoredOpportunity> ROW = (rs, row) -> new StoredOpportunity(
            rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
            rs.getObject("customer_id", UUID.class), rs.getString("customer_name"),
            rs.getObject("lead_id", UUID.class), rs.getString("lead_name"),
            rs.getObject("owner_user_id", UUID.class), rs.getString("owner_name"),
            rs.getString("title"), rs.getString("stage"), rs.getObject("expected_close_date", LocalDate.class),
            rs.getBigDecimal("expected_value"), rs.getString("currency_code").strip(),
            rs.getBigDecimal("actual_value"), rs.getObject("actual_close_date", LocalDate.class),
            rs.getString("notes"), rs.getString("closing_reason"),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public OpportunityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long count(String where, List<Object> args) {
        Long result = jdbc.queryForObject("SELECT count(*)" + FROM + " WHERE " + where, Long.class, args.toArray());
        return result == null ? 0 : result;
    }

    public List<StoredOpportunity> list(String where, List<Object> args, int page, int size) {
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((long) page * size);
        return jdbc.query(SELECT + FROM + " WHERE " + where
                        + " ORDER BY o.updated_at DESC,o.id LIMIT ? OFFSET ?",
                ROW, queryArgs.toArray());
    }

    public Optional<StoredOpportunity> find(String where, List<Object> args, UUID id) {
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(id);
        return jdbc.query(SELECT + FROM + " WHERE " + where + " AND o.id=?", ROW, queryArgs.toArray())
                .stream().findFirst();
    }

    public Map<UUID, List<OpportunityLineResponse>> lines(UUID organizationId, Collection<UUID> opportunityIds) {
        if (opportunityIds.isEmpty()) return Map.of();
        List<UUID> ids = List.copyOf(opportunityIds);
        List<Object> args = new ArrayList<>(List.of(organizationId));
        args.addAll(ids);
        String sql = """
                SELECT ol.id,ol.opportunity_id,ol.offering_id,o.code AS offering_code,o.name AS offering_name,
                       ol.quantity,ol.unit_price,ol.discount_percent,ol.line_value,ol.legacy_value_only
                FROM opportunity_lines ol
                JOIN offerings o ON o.id=ol.offering_id AND o.organization_id=ol.organization_id
                WHERE ol.organization_id=? AND ol.opportunity_id IN (
                """ + FoundationAccess.placeholders(ids.size()) + ") ORDER BY ol.opportunity_id,ol.line_number";
        Map<UUID, List<OpportunityLineResponse>> result = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            UUID opportunityId = rs.getObject("opportunity_id", UUID.class);
            result.computeIfAbsent(opportunityId, ignored -> new ArrayList<>()).add(new OpportunityLineResponse(
                    rs.getObject("id", UUID.class), rs.getObject("offering_id", UUID.class),
                    rs.getString("offering_code"), rs.getString("offering_name"), rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"), rs.getBigDecimal("discount_percent"),
                    rs.getBigDecimal("line_value"), rs.getBoolean("legacy_value_only")));
        }, args.toArray());
        result.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(result);
    }

    public void insert(UUID id, UUID organizationId, UUID customerId, UUID leadId, UUID ownerUserId,
                       String title, LocalDate expectedCloseDate, java.math.BigDecimal expectedValue,
                       String notes, List<OpportunityLineInput> lines, Instant now) {
        jdbc.update("""
                INSERT INTO opportunities(id,organization_id,customer_id,lead_id,offering_id,owner_user_id,
                    title,stage,expected_value,currency_code,expected_close_date,closing_reason,closed_at,
                    notes,actual_value,actual_close_date,created_at,updated_at)
                VALUES (?,?,?,?,?, ?,?,'NEW',?,'VND',?,NULL,NULL,?,NULL,NULL,?,?)
                """, id, organizationId, customerId, leadId, lines.getFirst().offeringId(), ownerUserId,
                title, expectedValue, java.sql.Date.valueOf(expectedCloseDate), notes,
                Timestamp.from(now), Timestamp.from(now));
        insertLines(organizationId, id, lines, now);
    }

    public void update(UUID organizationId, UUID id, UUID customerId, UUID leadId, UUID ownerUserId,
                       UUID legacyOfferingId, String title, String stage, java.math.BigDecimal expectedValue,
                       LocalDate expectedCloseDate, String closingReason, Instant closedAt,
                       String notes, java.math.BigDecimal actualValue, LocalDate actualCloseDate, Instant now) {
        jdbc.update("""
                UPDATE opportunities SET customer_id=?,lead_id=?,offering_id=?,owner_user_id=?,title=?,stage=?,
                    expected_value=?,expected_close_date=?,closing_reason=?,closed_at=?,notes=?,actual_value=?,
                    actual_close_date=?,updated_at=? WHERE organization_id=? AND id=?
                """, customerId, leadId, legacyOfferingId, ownerUserId, title, stage, expectedValue,
                expectedCloseDate == null ? null : java.sql.Date.valueOf(expectedCloseDate), closingReason,
                closedAt == null ? null : Timestamp.from(closedAt), notes, actualValue,
                actualCloseDate == null ? null : java.sql.Date.valueOf(actualCloseDate), Timestamp.from(now),
                organizationId, id);
    }

    public void replaceLines(UUID organizationId, UUID opportunityId, List<OpportunityLineInput> lines, Instant now) {
        jdbc.update("DELETE FROM opportunity_lines WHERE organization_id=? AND opportunity_id=?", organizationId, opportunityId);
        insertLines(organizationId, opportunityId, lines, now);
    }

    private void insertLines(UUID organizationId, UUID opportunityId, List<OpportunityLineInput> lines, Instant now) {
        int lineNumber = 1;
        for (OpportunityLineInput line : lines) {
            jdbc.update("""
                    INSERT INTO opportunity_lines(id,organization_id,opportunity_id,line_number,offering_id,
                        quantity,unit_price,discount_percent,line_value,legacy_value_only,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,false,?,?)
                    """, UUID.randomUUID(), organizationId, opportunityId, lineNumber++, line.offeringId(),
                    line.quantity(), line.unitPrice(), line.discountPercent(), line.lineValue(),
                    Timestamp.from(now), Timestamp.from(now));
        }
    }

    public record StoredOpportunity(UUID id, UUID organizationId, UUID customerId, String customerName,
                                    UUID leadId, String leadName, UUID ownerUserId, String ownerName,
                                    String title, String stage, LocalDate expectedCloseDate,
                                    java.math.BigDecimal expectedValue, String currencyCode,
                                    java.math.BigDecimal actualValue, LocalDate actualCloseDate,
                                    String notes, String closingReason, Instant closedAt,
                                    Instant createdAt, Instant updatedAt) {
        public OpportunityResponse response(List<OpportunityLineResponse> lines) {
            return new OpportunityResponse(id, title, customerId, customerName, leadId, leadName,
                    ownerUserId, ownerName, stage, expectedCloseDate, expectedValue, currencyCode,
                    actualValue, actualCloseDate, notes, closingReason, lines, createdAt, updatedAt);
        }
    }
}
