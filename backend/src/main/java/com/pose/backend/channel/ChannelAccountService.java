package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.identity.FoundationAccess;
import com.pose.backend.identity.PermissionCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ChannelAccountService {
    private static final Set<String> TYPES = Set.of("TELEGRAM", "ZALO_SIM");
    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,179}");
    private final JdbcTemplate jdbc;
    private final FoundationAccess access;
    private final FoundationAuditRepository audit;
    private final Clock clock;

    public ChannelAccountService(JdbcTemplate jdbc, FoundationAccess access,
                                 FoundationAuditRepository audit, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ChannelAccountResponse> list(ActorContext incoming) {
        ActorContext actor = access.lockAndRefresh(incoming);
        access.requireAdmin(actor, PermissionCode.CHANNEL_MANAGE);
        return jdbc.query("""
                SELECT id, channel_type, display_name, external_account_id, status, secret_ref, created_at, updated_at
                FROM channel_accounts WHERE organization_id=? ORDER BY channel_type, display_name, id
                """, (rs, row) -> new ChannelAccountResponse(rs.getObject("id", UUID.class),
                rs.getString("channel_type"), rs.getString("display_name"), rs.getString("external_account_id"),
                rs.getString("status"), rs.getString("secret_ref"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), actor.organizationId());
    }

    @Transactional
    public ChannelAccountResponse create(ActorContext incoming, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        access.requireAdmin(actor, PermissionCode.CHANNEL_MANAGE);
        JsonInput in = JsonInput.of(body, "channelType", "displayName", "externalAccountId", "secretRef");
        String type = type(in.text("channelType", 30, true, false));
        String name = in.text("displayName", 160, true, false);
        String externalId = in.text("externalAccountId", 180, true, false);
        String secretRef = secretRef(in.text("secretRef", 180, true, false));
        if (exists(actor.organizationId(), type, externalId)) throw ApiRequestException.conflict("CHANNEL_ACCOUNT_EXISTS");

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO channel_accounts(id, organization_id, channel_type, display_name, external_account_id,
                        status, secret_ref, created_at, updated_at)
                    VALUES (?,?,?,?,?,'ACTIVE',?,?,?)
                    """, id, actor.organizationId(), type, name, externalId, secretRef,
                    Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw ApiRequestException.conflict("CHANNEL_ACCOUNT_EXISTS");
        }
        audit.record(actor, "CHANNEL_ACCOUNT_CREATED", "CHANNEL_ACCOUNT", id, null,
                Map.of("channelType", type, "externalAccountId", externalId, "status", "ACTIVE"), now);
        return find(actor.organizationId(), id);
    }

    @Transactional
    public ChannelAccountResponse patch(ActorContext incoming, UUID id, JsonNode body) {
        ActorContext actor = access.lockAndRefresh(incoming);
        access.requireAdmin(actor, PermissionCode.CHANNEL_MANAGE);
        ChannelAccountResponse before = find(actor.organizationId(), id);
        JsonInput in = JsonInput.of(body, "displayName", "status", "secretRef");
        in.requireAny();
        String name = in.has("displayName") ? in.text("displayName", 160, true, false) : before.displayName();
        String status = in.has("status") ? status(in.text("status", 20, true, false)) : before.status();
        String secretRef = in.has("secretRef") ? secretRef(in.text("secretRef", 180, true, false)) : before.secretRef();
        if (name.equals(before.displayName()) && status.equals(before.status()) && secretRef.equals(before.secretRef())) return before;
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE channel_accounts SET display_name=?, status=?, secret_ref=?, updated_at=?
                WHERE organization_id=? AND id=?
                """, name, status, secretRef, Timestamp.from(now), actor.organizationId(), id);
        audit.record(actor, "CHANNEL_ACCOUNT_UPDATED", "CHANNEL_ACCOUNT", id,
                Map.of("channelType", before.channelType(), "status", before.status()),
                Map.of("channelType", before.channelType(), "status", status), now);
        return find(actor.organizationId(), id);
    }

    private ChannelAccountResponse find(UUID organizationId, UUID id) {
        return jdbc.query("""
                SELECT id, channel_type, display_name, external_account_id, status, secret_ref, created_at, updated_at
                FROM channel_accounts WHERE organization_id=? AND id=?
                """, rs -> rs.next() ? new ChannelAccountResponse(rs.getObject("id", UUID.class),
                rs.getString("channel_type"), rs.getString("display_name"), rs.getString("external_account_id"),
                rs.getString("status"), rs.getString("secret_ref"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()) : null, organizationId, id);
    }

    private boolean exists(UUID organizationId, String type, String externalId) {
        return jdbc.queryForObject("SELECT count(*) FROM channel_accounts WHERE organization_id=? AND channel_type=? AND external_account_id=?",
                Integer.class, organizationId, type, externalId) > 0;
    }

    private static String type(String value) {
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (!TYPES.contains(normalized)) throw JsonInput.invalid("channelType", "Chỉ hỗ trợ TELEGRAM hoặc ZALO_SIM trong W13.");
        return normalized;
    }

    private static String status(String value) {
        String normalized = value.toUpperCase(java.util.Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE").contains(normalized)) throw JsonInput.invalid("status", "Trạng thái không hợp lệ.");
        return normalized;
    }

    private static String secretRef(String value) {
        if (!ENV_NAME.matcher(value).matches()) throw JsonInput.invalid("secretRef", "Chỉ nhập tên biến môi trường, không nhập giá trị secret.");
        return value;
    }
}
