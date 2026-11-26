package com.pose.backend.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.api.JsonInput;
import com.pose.backend.audit.FoundationAuditRepository;
import com.pose.backend.conversation.ConversationInteractionSessionService;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class ChannelWebhookService {
    private static final int MAX_TEXT_LENGTH = 4000;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final FoundationAuditRepository audit;
    private final Clock clock;
    private final ConversationInteractionSessionService interactionSessions;

    public ChannelWebhookService(JdbcTemplate jdbc, Environment environment,
                                 FoundationAuditRepository audit, Clock clock,
                                 ConversationInteractionSessionService interactionSessions) {
        this.jdbc = jdbc;
        this.environment = environment;
        this.audit = audit;
        this.clock = clock;
        this.interactionSessions = interactionSessions;
    }

    @Transactional
    public ChannelWebhookAck receive(String pathType, String suppliedSecret, JsonNode payload) {
        String type = normalizeType(pathType);
        InboundMessage message = type.equals("TELEGRAM") ? telegram(payload) : zaloSim(payload);
        ResolvedChannelAccount account = resolveAccount(type, message.externalAccountId(), suppliedSecret);
        return persistInbound(account, message, type);
    }

    @Transactional
    public MessengerWebhookAck receiveMessenger(byte[] rawBody, String suppliedSignature, JsonNode payload) {
        if (rawBody == null || rawBody.length == 0 || rawBody.length > 64 * 1024) {
            throw invalid("body", "Payload webhook không hợp lệ.");
        }
        List<MessengerPageBatch> batches = messengerBatches(payload);
        Map<String, ResolvedChannelAccount> accountsByPage = new LinkedHashMap<>();
        for (MessengerPageBatch batch : batches) {
            accountsByPage.computeIfAbsent(batch.pageId(), pageId ->
                    resolveMessengerAccount(pageId, rawBody, suppliedSignature));
        }

        int accepted = 0;
        int duplicates = 0;
        for (MessengerPageBatch batch : batches) {
            ResolvedChannelAccount account = accountsByPage.get(batch.pageId());
            for (InboundMessage message : batch.messages()) {
                ChannelWebhookAck ack = persistInbound(account, message, "MESSENGER");
                if (ack.duplicate()) duplicates++;
                else accepted++;
            }
        }
        return new MessengerWebhookAck(true, accepted, duplicates);
    }

    @Transactional(readOnly = true)
    public String verifyMessengerChallenge(String mode, String suppliedToken, String challenge) {
        String expected = environment.getProperty("POSE_CHANNEL_MESSENGER_VERIFY_TOKEN");
        if (expected == null || expected.isBlank()) throw notConfigured();
        if (!"subscribe".equals(mode) || challenge == null || challenge.isBlank() || challenge.length() > 512
                || suppliedToken == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw unauthorized();
        }
        return challenge;
    }

    private ChannelWebhookAck persistInbound(ResolvedChannelAccount account, InboundMessage message, String type) {
        Instant now = clock.instant();

        boolean postgres = isPostgres();
        String insertConversationSql = postgres ? """
                INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id,
                    assigned_user_id, status, consent_status, last_inbound_at, last_message_at, created_at, updated_at)
                VALUES (?,?,?,?,?,'OPEN','UNKNOWN',?,?,?,?)
                ON CONFLICT (channel_account_id, external_thread_id) DO NOTHING
                """ : """
                INSERT INTO conversations(id, organization_id, channel_account_id, external_thread_id,
                    assigned_user_id, status, consent_status, last_inbound_at, last_message_at, created_at, updated_at)
                SELECT ?,?,?,?,?, 'OPEN','UNKNOWN',?,?,?,?
                WHERE NOT EXISTS (SELECT 1 FROM conversations WHERE channel_account_id=? AND external_thread_id=?)
                """;
        Object[] conversationParams = postgres
                ? new Object[]{UUID.randomUUID(), account.organizationId(), account.id(), message.threadId(), account.defaultAssigneeUserId(),
                    Timestamp.from(message.occurredAt()), Timestamp.from(message.occurredAt()),
                    Timestamp.from(now), Timestamp.from(now)}
                : new Object[]{UUID.randomUUID(), account.organizationId(), account.id(), message.threadId(), account.defaultAssigneeUserId(),
                    Timestamp.from(message.occurredAt()), Timestamp.from(message.occurredAt()),
                    Timestamp.from(now), Timestamp.from(now), account.id(), message.threadId()};
        jdbc.update(insertConversationSql, conversationParams);

        UUID conversationId = jdbc.queryForObject("""
                SELECT id FROM conversations WHERE organization_id=? AND channel_account_id=? AND external_thread_id=? FOR UPDATE
                """, UUID.class, account.organizationId(), account.id(), message.threadId());

        UUID messageId = UUID.randomUUID();
        String insertMessageSql = postgres ? """
                INSERT INTO conversation_messages(id, organization_id, conversation_id, direction,
                    external_message_id, content_type, content, delivery_status, occurred_at, created_at)
                VALUES (?,?,?,'INBOUND',?,'TEXT',?,'RECEIVED',?,?)
                ON CONFLICT (conversation_id, direction, external_message_id) DO NOTHING
                """ : """
                INSERT INTO conversation_messages(id, organization_id, conversation_id, direction,
                    external_message_id, content_type, content, delivery_status, occurred_at, created_at)
                SELECT ?,?,?, 'INBOUND',?,'TEXT',?,'RECEIVED',?,?
                WHERE NOT EXISTS (SELECT 1 FROM conversation_messages
                    WHERE conversation_id=? AND direction='INBOUND' AND external_message_id=?)
                """;
        Object[] messageParams = postgres
                ? new Object[]{messageId, account.organizationId(), conversationId, message.externalMessageId(),
                    message.content(), Timestamp.from(message.occurredAt()), Timestamp.from(now)}
                : new Object[]{messageId, account.organizationId(), conversationId, message.externalMessageId(),
                    message.content(), Timestamp.from(message.occurredAt()), Timestamp.from(now),
                    conversationId, message.externalMessageId()};
        int inserted = jdbc.update(insertMessageSql, messageParams);

        boolean duplicate = inserted == 0;
        if (!duplicate) {
            jdbc.update("""
                    UPDATE conversations SET last_inbound_at=CASE WHEN last_inbound_at IS NULL OR last_inbound_at<? THEN ? ELSE last_inbound_at END,
                        last_message_at=CASE WHEN last_message_at IS NULL OR last_message_at<? THEN ? ELSE last_message_at END,
                        updated_at=?
                    WHERE organization_id=? AND id=?
                    """, Timestamp.from(message.occurredAt()), Timestamp.from(message.occurredAt()),
                    Timestamp.from(message.occurredAt()), Timestamp.from(message.occurredAt()),
                    Timestamp.from(now), account.organizationId(), conversationId);
            interactionSessions.attach(account.organizationId(), conversationId, messageId, type,
                    message.occurredAt(), now);
            audit.recordSystem(account.organizationId(), "channel:" + type, "MESSAGE_RECEIVED",
                    "CONVERSATION_MESSAGE", messageId,
                    null, java.util.Map.of("conversationId", conversationId, "channelType", type,
                            "direction", "INBOUND", "contentType", "TEXT"), now);
            publishMessageReceived(account.organizationId(), conversationId, messageId,
                    message.externalMessageId(), now, postgres);
        }
        return new ChannelWebhookAck(true, duplicate, conversationId);
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                connection -> "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
    }

    private void publishMessageReceived(UUID organizationId, UUID conversationId, UUID messageId,
                                        String externalMessageId, Instant occurredAt, boolean postgres) {
        String key = "outbox.message-received.v1:" + conversationId + ":" + sha256(externalMessageId);
        String payload = "{\"conversationId\":\"" + conversationId + "\",\"messageId\":\"" + messageId + "\"}";
        String conflictClause = postgres
                ? " ON CONFLICT (organization_id,idempotency_key) DO NOTHING"
                : " ON CONFLICT DO NOTHING";
        jdbc.update("""
                INSERT INTO outbox_events(id,organization_id,aggregate_type,aggregate_id,event_type,payload,
                    idempotency_key,delivery_status,retry_count,occurred_at)
                VALUES (?,?,'CONVERSATION',?,'MESSAGE_RECEIVED',CAST(? AS jsonb),?,'PENDING',0,?)
                """ + conflictClause, UUID.randomUUID(), organizationId, conversationId, payload, key,
                Timestamp.from(occurredAt));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ResolvedChannelAccount resolveAccount(String type, String externalAccountId, String suppliedSecret) {
        List<ResolvedChannelAccount> accounts = jdbc.query("""
                SELECT id, organization_id, display_name, external_account_id, secret_ref, default_assignee_user_id
                FROM channel_accounts WHERE channel_type=? AND status='ACTIVE'
                """, (rs, row) -> new ResolvedChannelAccount(rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getString("external_account_id"),
                rs.getString("secret_ref"), rs.getObject("default_assignee_user_id", UUID.class)), type);
        if (accounts.isEmpty()) throw notConfigured();

        List<ResolvedChannelAccount> candidates = externalAccountId == null
                ? accounts : accounts.stream().filter(a -> a.externalAccountId().equals(externalAccountId)).toList();
        if (candidates.isEmpty()) throw unauthorized();

        List<ResolvedChannelAccount> configured = new ArrayList<>();
        List<ResolvedChannelAccount> matches = new ArrayList<>();
        for (ResolvedChannelAccount account : candidates) {
            String expected = environment.getProperty(account.secretRef());
            if (expected == null || expected.isBlank()) continue;
            configured.add(account);
            if (suppliedSecret != null && !suppliedSecret.isBlank() && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
                matches.add(account);
            }
        }
        if (configured.isEmpty()) throw notConfigured();
        if (matches.isEmpty()) throw unauthorized();
        if (matches.size() != 1) {
            throw new ChannelWebhookException(503, "CHANNEL_WEBHOOK_AMBIGUOUS",
                    "Webhook đang có cấu hình kênh không duy nhất.");
        }
        return matches.getFirst();
    }

    private ResolvedChannelAccount resolveMessengerAccount(String pageId, byte[] rawBody, String suppliedSignature) {
        List<ResolvedChannelAccount> accounts = jdbc.query("""
                SELECT id, organization_id, display_name, external_account_id, secret_ref, default_assignee_user_id
                FROM channel_accounts WHERE channel_type='MESSENGER' AND external_account_id=? AND status='ACTIVE'
                """, (rs, row) -> new ResolvedChannelAccount(rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getString("external_account_id"),
                rs.getString("secret_ref"), rs.getObject("default_assignee_user_id", UUID.class)), pageId);
        if (accounts.isEmpty()) throw notConfigured();
        if (suppliedSignature == null || !suppliedSignature.matches("(?i)^sha256=[0-9a-f]{64}$")) {
            throw unauthorized();
        }

        String normalizedSignature = suppliedSignature.substring("sha256=".length()).toLowerCase(java.util.Locale.ROOT);
        List<ResolvedChannelAccount> configured = new ArrayList<>();
        List<ResolvedChannelAccount> matches = new ArrayList<>();
        for (ResolvedChannelAccount account : accounts) {
            String appSecret = environment.getProperty(account.secretRef());
            if (appSecret == null || appSecret.isBlank()) continue;
            configured.add(account);
            if (MessageDigest.isEqual(hmacSha256(rawBody, appSecret), normalizedSignature.getBytes(StandardCharsets.US_ASCII))) {
                matches.add(account);
            }
        }
        if (configured.isEmpty()) throw notConfigured();
        if (matches.isEmpty()) throw unauthorized();
        if (matches.size() != 1) {
            throw new ChannelWebhookException(503, "CHANNEL_WEBHOOK_AMBIGUOUS",
                    "Webhook đang có cấu hình kênh không duy nhất.");
        }
        return matches.getFirst();
    }

    private static byte[] hmacSha256(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(body)).getBytes(StandardCharsets.US_ASCII);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    private static List<MessengerPageBatch> messengerBatches(JsonNode payload) {
        if (payload == null || !payload.isObject() || !"page".equalsIgnoreCase(payload.path("object").asText())) {
            throw invalid("body", "Payload Messenger phải thuộc object page.");
        }
        JsonNode entries = payload.get("entry");
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            throw invalid("entry", "Payload Messenger phải có danh sách entry.");
        }
        List<MessengerPageBatch> batches = new ArrayList<>();
        for (JsonNode entry : entries) {
            if (!entry.isObject()) throw invalid("entry", "Entry Messenger không hợp lệ.");
            String pageId = externalId(entry.get("id"), "entry.id");
            JsonNode events = entry.get("messaging");
            if (events == null || !events.isArray()) throw invalid("entry.messaging", "Danh sách messaging không hợp lệ.");
            List<InboundMessage> messages = new ArrayList<>();
            for (JsonNode event : events) {
                if (!event.isObject()) throw invalid("entry.messaging", "Sự kiện Messenger không hợp lệ.");
                JsonNode recipient = event.get("recipient");
                if (recipient != null && recipient.isObject() && recipient.has("id")
                        && !pageId.equals(externalId(recipient.get("id"), "recipient.id"))) {
                    throw invalid("recipient.id", "Page nhận sự kiện Messenger không khớp entry.");
                }
                JsonNode message = event.get("message");
                if (message == null || !message.isObject() || message.path("is_echo").asBoolean(false)) continue;
                JsonNode text = message.get("text");
                if (text == null || !text.isTextual() || text.asText().isBlank()) continue;
                JsonNode sender = childObject(event, "sender", "sender");
                String threadId = externalId(sender.get("id"), "sender.id");
                String externalMessageId = externalId(message.get("mid"), "message.mid");
                Instant occurredAt = timestamp(event.get("timestamp"));
                messages.add(new InboundMessage(pageId, threadId, externalMessageId,
                        requiredText(text, "message.text"), occurredAt));
            }
            batches.add(new MessengerPageBatch(pageId, messages));
        }
        return List.copyOf(batches);
    }

    private static InboundMessage telegram(JsonNode payload) {
        JsonNode message = object(payload, "message");
        long messageId = nonNegativeLong(message.get("message_id"), "message.message_id");
        JsonNode chat = childObject(message, "chat", "message.chat");
        String threadId = numericId(chat.get("id"), "message.chat.id");
        childObject(message, "from", "message.from");
        Instant occurredAt = Instant.ofEpochSecond(nonNegativeLong(message.get("date"), "message.date"));
        String content = requiredText(message.get("text"), "message.text");
        return new InboundMessage(null, threadId, Long.toString(messageId), content, occurredAt);
    }

    private static InboundMessage zaloSim(JsonNode payload) {
        if (payload == null || !payload.isObject()) throw invalid("body", "Payload webhook phải là JSON object.");
        requiredText(payload.get("event_name"), "event_name");
        Instant occurredAt = timestamp(payload.get("timestamp"));
        JsonNode sender = childObject(payload, "sender", "sender");
        JsonNode recipient = childObject(payload, "recipient", "recipient");
        String threadId = externalId(sender.get("id"), "sender.id");
        String accountId = externalId(recipient.get("id"), "recipient.id");
        JsonNode message = childObject(payload, "message", "message");
        String externalMessageId = externalId(message.get("msg_id"), "message.msg_id");
        String content = requiredText(message.get("text"), "message.text");
        return new InboundMessage(accountId, threadId, externalMessageId, content, occurredAt);
    }

    private static JsonNode object(JsonNode root, String field) {
        if (root == null || !root.isObject()) throw invalid("body", "Payload webhook phải là JSON object.");
        return childObject(root, field, field);
    }

    private static JsonNode childObject(JsonNode root, String field, String errorField) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isObject()) throw invalid(errorField, "Trường phải là object.");
        return value;
    }

    private static long nonNegativeLong(JsonNode value, String field) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
            throw invalid(field, "Giá trị phải là số nguyên không âm.");
        return value.longValue();
    }

    private static String numericId(JsonNode value, String field) {
        if (value != null && value.isIntegralNumber() && value.canConvertToLong()) return Long.toString(value.longValue());
        return externalId(value, field);
    }

    private static String externalId(JsonNode value, String field) {
        String result;
        if (value != null && value.isTextual()) result = value.asText().strip();
        else if (value != null && value.isIntegralNumber()) result = value.asText();
        else throw invalid(field, "Mã ngoài kênh không hợp lệ.");
        if (result.isBlank() || result.length() > 180) throw invalid(field, "Mã ngoài kênh không hợp lệ.");
        return result;
    }

    private static String requiredText(JsonNode value, String field) {
        if (value == null || !value.isTextual()) throw invalid(field, "Nội dung phải là chuỗi.");
        String text = value.asText().strip();
        if (text.isBlank() || text.length() > MAX_TEXT_LENGTH) throw invalid(field, "Nội dung không được trống và tối đa 4000 ký tự.");
        return text;
    }

    private static Instant timestamp(JsonNode value) {
        if (value == null) throw invalid("timestamp", "Thiếu thời điểm tin nhắn.");
        try {
            if (value.isIntegralNumber() && value.canConvertToLong()) {
                long raw = value.longValue();
                return raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
            }
            if (value.isTextual()) return OffsetDateTime.parse(value.asText()).toInstant();
        } catch (RuntimeException exception) {
            throw invalid("timestamp", "Thời điểm tin nhắn không hợp lệ.");
        }
        throw invalid("timestamp", "Thời điểm tin nhắn không hợp lệ.");
    }

    private static String normalizeType(String pathType) {
        String normalized = pathType == null ? "" : pathType.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("TELEGRAM") && !normalized.equals("ZALO_SIM"))
            throw invalid("type", "Kênh webhook không được hỗ trợ.");
        return normalized;
    }

    private static ChannelWebhookException unauthorized() {
        return new ChannelWebhookException(401, "CHANNEL_WEBHOOK_UNAUTHORIZED", "Webhook không được xác thực.");
    }

    private static ChannelWebhookException notConfigured() {
        return new ChannelWebhookException(503, "CHANNEL_WEBHOOK_NOT_CONFIGURED", "Kênh webhook chưa được cấu hình.");
    }

    private static RuntimeException invalid(String field, String message) {
        return JsonInput.invalid(field, message);
    }

    private record ResolvedChannelAccount(UUID id, UUID organizationId, String externalAccountId, String secretRef,
                                          UUID defaultAssigneeUserId) {}
    private record InboundMessage(String externalAccountId, String threadId, String externalMessageId,
                                  String content, Instant occurredAt) {}
    private record MessengerPageBatch(String pageId, List<InboundMessage> messages) {}
}
