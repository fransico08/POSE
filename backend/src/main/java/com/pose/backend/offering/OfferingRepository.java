package com.pose.backend.offering;

import com.pose.backend.identity.FoundationAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class OfferingRepository {
    // Correlated to offering alias o; shared by list and detail to keep sales scope identical.
    public static final String SALES_READ_SCOPE = """
            o.status='ACTIVE' AND EXISTS (
              SELECT 1 FROM sales_assignments sa
              JOIN team_memberships tm ON tm.organization_id=sa.organization_id AND tm.user_id=sa.sales_user_id
                AND tm.team_id=o.team_id
              JOIN teams t ON t.id=tm.team_id AND t.organization_id=tm.organization_id
              WHERE sa.organization_id=o.organization_id AND sa.offering_id=o.id AND sa.sales_user_id=?
                AND sa.status='ACTIVE' AND sa.revoked_at IS NULL AND sa.effective_from<=?
                AND (sa.effective_to IS NULL OR sa.effective_to>=?)
                AND tm.status='ACTIVE' AND tm.joined_at<=? AND (tm.left_at IS NULL OR tm.left_at>?)
                AND t.status='ACTIVE'
            )
            """;
    private static final String SELECT_OFFERING = """
            SELECT o.id, o.organization_id, o.category_id, c.name AS category_name,
                   o.team_id, t.name AS team_name, o.code, o.name, o.description,
                   o.kind, o.unit, o.status, o.created_at, o.updated_at
            FROM offerings o
            LEFT JOIN offering_categories c
              ON c.id = o.category_id AND c.organization_id = o.organization_id
            LEFT JOIN teams t
              ON t.id = o.team_id AND t.organization_id = o.organization_id
            """;

    private static final RowMapper<StoredOffering> OFFERING_MAPPER = (rs, row) -> new StoredOffering(
            rs.getObject("id", UUID.class),
            rs.getObject("organization_id", UUID.class),
            rs.getObject("category_id", UUID.class),
            rs.getString("category_name"),
            rs.getObject("team_id", UUID.class),
            rs.getString("team_name"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("kind"),
            rs.getString("unit"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public OfferingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredOffering> find(UUID organizationId, UUID id) {
        return jdbc.query(SELECT_OFFERING + " WHERE o.organization_id=? AND o.id=?",
                OFFERING_MAPPER, organizationId, id).stream().findFirst();
    }

    public long count(String where, List<Object> args) {
        return jdbc.queryForObject("SELECT count(*) FROM offerings o WHERE " + where,
                Long.class, args.toArray());
    }

    public List<StoredOffering> list(String where, List<Object> args, int page, int size) {
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((long) page * size);
        return jdbc.query(SELECT_OFFERING + " WHERE " + where
                        + " ORDER BY lower(o.name), o.id LIMIT ? OFFSET ?",
                OFFERING_MAPPER, queryArgs.toArray());
    }

    public Map<UUID, StoredPrice> currentPrices(UUID organizationId, Collection<UUID> offeringIds, Instant now) {
        if (offeringIds.isEmpty()) return Map.of();
        List<UUID> ids = List.copyOf(offeringIds);
        String placeholders = FoundationAccess.placeholders(ids.size());
        List<Object> args = new ArrayList<>(List.of(organizationId));
        args.addAll(ids);
        args.add(Timestamp.from(now));
        args.add(Timestamp.from(now));
        String sql = """
                SELECT id, offering_id, amount, currency_code, pricing_unit, effective_from
                FROM (
                    SELECT p.id, p.offering_id, p.amount, p.currency_code, p.pricing_unit, p.effective_from,
                           ROW_NUMBER() OVER (PARTITION BY p.offering_id ORDER BY p.effective_from DESC, p.id DESC) AS row_number
                    FROM offering_prices p
                    WHERE p.organization_id=? AND p.offering_id IN (
                """ + placeholders + """
                      )
                      AND p.effective_from<=? AND (p.effective_to IS NULL OR p.effective_to>?)
                ) current_prices
                WHERE row_number=1
                """;
        List<StoredPrice> rows = jdbc.query(sql, (rs, row) -> new StoredPrice(
                rs.getObject("id", UUID.class),
                rs.getObject("offering_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code").strip(),
                rs.getString("pricing_unit"),
                rs.getTimestamp("effective_from").toInstant(),
                null), args.toArray());
        Map<UUID, StoredPrice> result = new HashMap<>();
        rows.forEach(price -> result.put(price.offeringId(), price));
        return Map.copyOf(result);
    }

    public List<StoredPrice> lockPriceRows(UUID organizationId, UUID offeringId) {
        return jdbc.query("""
                SELECT id, amount, currency_code, pricing_unit, effective_from, effective_to
                FROM offering_prices
                WHERE organization_id=? AND offering_id=?
                ORDER BY effective_from, id
                FOR UPDATE
                """, (rs, row) -> new StoredPrice(
                rs.getObject("id", UUID.class),
                offeringId,
                rs.getBigDecimal("amount"),
                rs.getString("currency_code").strip(),
                rs.getString("pricing_unit"),
                rs.getTimestamp("effective_from").toInstant(),
                rs.getTimestamp("effective_to") == null ? null : rs.getTimestamp("effective_to").toInstant()),
                organizationId, offeringId);
    }

    public void create(UUID organizationId, UUID id, UUID categoryId, UUID teamId, String code,
                       String name, String description, String kind, String unit, String status,
                       Instant now) {
        jdbc.update("""
                INSERT INTO offerings(id,organization_id,category_id,team_id,code,name,description,kind,unit,status,metadata,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CAST('{}' AS jsonb),?,?)
                """, id, organizationId, categoryId, teamId, code, name, description, kind, unit, status,
                Timestamp.from(now), Timestamp.from(now));
    }

    public void update(UUID organizationId, UUID id, UUID categoryId, UUID teamId,
                       String name, String description, String kind, String unit, Instant now) {
        jdbc.update("""
                UPDATE offerings
                SET category_id=?, team_id=?, name=?, description=?, kind=?, unit=?, updated_at=?
                WHERE organization_id=? AND id=?
                """, categoryId, teamId, name, description, kind, unit, Timestamp.from(now), organizationId, id);
    }

    public void updateStatus(UUID organizationId, UUID id, String status, Instant now) {
        jdbc.update("UPDATE offerings SET status=?,updated_at=? WHERE organization_id=? AND id=?",
                status, Timestamp.from(now), organizationId, id);
    }

    public Optional<StoredPrice> findCurrentPrice(UUID organizationId, UUID offeringId, Instant now) {
        return currentPrices(organizationId, List.of(offeringId), now).values().stream().findFirst();
    }

    public void closePrice(UUID organizationId, UUID offeringId, UUID priceId, Instant effectiveTo) {
        jdbc.update("""
                UPDATE offering_prices SET effective_to=?
                WHERE organization_id=? AND offering_id=? AND id=?
                """, Timestamp.from(effectiveTo), organizationId, offeringId, priceId);
    }

    public void insertPrice(UUID organizationId, UUID offeringId, BigDecimal amount, String currencyCode,
                            String pricingUnit, Instant effectiveFrom, Instant effectiveTo) {
        jdbc.update("""
                INSERT INTO offering_prices(id,organization_id,offering_id,amount,currency_code,pricing_unit,effective_from,effective_to,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), organizationId, offeringId, amount, currencyCode, pricingUnit,
                Timestamp.from(effectiveFrom), effectiveTo == null ? null : Timestamp.from(effectiveTo),
                Timestamp.from(effectiveFrom));
    }

    public List<OfferingCategoryResponse> categories(UUID organizationId) {
        return jdbc.query("""
                SELECT id, code, name FROM offering_categories
                WHERE organization_id=? AND status='ACTIVE'
                ORDER BY lower(name), id
                """, (rs, row) -> new OfferingCategoryResponse(
                rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name")), organizationId);
    }

    public Optional<String> categoryStatus(UUID organizationId, UUID categoryId) {
        return jdbc.query("SELECT status FROM offering_categories WHERE organization_id=? AND id=?",
                (rs, row) -> rs.getString("status"), organizationId, categoryId).stream().findFirst();
    }

    public Optional<String> teamStatus(UUID organizationId, UUID teamId) {
        return jdbc.query("SELECT status FROM teams WHERE organization_id=? AND id=?",
                (rs, row) -> rs.getString("status"), organizationId, teamId).stream().findFirst();
    }

    public boolean duplicateActiveName(UUID organizationId, String kind, String normalizedName, UUID excludeId) {
        Integer matches = jdbc.queryForObject("""
                SELECT count(*) FROM offerings
                WHERE organization_id=? AND status='ACTIVE' AND kind=?
                  AND lower(btrim(name))=? AND id<>?
                """, Integer.class, organizationId, kind, normalizedName, excludeId);
        return matches != null && matches > 0;
    }

    public Optional<String> salesAssignmentScope(UUID organizationId, UUID offeringId, UUID salesUserId, Instant now) {
        return jdbc.query("SELECT 'ACTIVE' FROM offerings o WHERE o.organization_id=? AND o.id=? AND " + SALES_READ_SCOPE,
                (rs, row) -> rs.getString(1), organizationId, offeringId, salesUserId,
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)).stream().findFirst();
    }

    public record StoredOffering(
            UUID id, UUID organizationId, UUID categoryId, String categoryName,
            UUID teamId, String teamName, String code, String name, String description,
            String kind, String unit, String status, Instant createdAt, Instant updatedAt) {
    }

    public record StoredPrice(
            UUID id, UUID offeringId, BigDecimal amount, String currencyCode, String pricingUnit,
            Instant effectiveFrom, Instant effectiveTo) {
        public StoredPrice(UUID offeringId, BigDecimal amount, String currencyCode,
                           String pricingUnit, Instant effectiveFrom) {
            this(null, offeringId, amount, currencyCode, pricingUnit, effectiveFrom, null);
        }
    }
}
