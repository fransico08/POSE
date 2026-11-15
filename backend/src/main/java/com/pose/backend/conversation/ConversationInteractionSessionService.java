package com.pose.backend.conversation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Groups channel messages into a privacy-safe CRM Interaction session. */
@Service
public class ConversationInteractionSessionService {
    private static final Duration SESSION_GAP = Duration.ofHours(4);
    private static final String SYSTEM_SUMMARY = "Có tin nhắn mới trong hội thoại kênh.";

    private final JdbcTemplate jdbc;

    public ConversationInteractionSessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID attach(UUID organizationId, UUID conversationId, UUID messageId, String channelType,
                       Instant occurredAt, Instant now) {
        ConversationProfile profile = jdbc.queryForObject("""
                SELECT lead_id,customer_id FROM conversations WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new ConversationProfile(rs.getObject("lead_id", UUID.class),
                rs.getObject("customer_id", UUID.class)), organizationId, conversationId);

        List<SessionMessage> recent = jdbc.query("""
                SELECT m.interaction_id,m.occurred_at
                FROM conversation_messages m
                JOIN interactions i ON i.organization_id=m.organization_id AND i.id=m.interaction_id
                    AND i.recorded_by_actor_type='SYSTEM' AND i.conversation_id=m.conversation_id
                WHERE m.organization_id=? AND m.conversation_id=? AND m.id<>? AND m.interaction_id IS NOT NULL
                ORDER BY m.occurred_at DESC,m.id DESC LIMIT 1
                """, (rs, row) -> new SessionMessage(rs.getObject("interaction_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant()), organizationId, conversationId, messageId);

        UUID interactionId;
        if (!recent.isEmpty() && Duration.between(recent.getFirst().occurredAt(), occurredAt).abs().compareTo(SESSION_GAP) <= 0) {
            interactionId = recent.getFirst().interactionId();
            jdbc.update("""
                    UPDATE interactions SET occurred_at=CASE WHEN occurred_at<? THEN ? ELSE occurred_at END,
                        lead_id=?,customer_id=?,updated_at=CASE WHEN updated_at<? THEN ? ELSE updated_at END
                    WHERE organization_id=? AND id=? AND recorded_by_actor_type='SYSTEM' AND conversation_id=?
                    """, Timestamp.from(occurredAt), Timestamp.from(occurredAt), profile.leadId(), profile.customerId(),
                    Timestamp.from(now), Timestamp.from(now), organizationId, interactionId, conversationId);
        } else {
            interactionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO interactions(id,organization_id,customer_id,lead_id,opportunity_id,conversation_id,
                        recorded_by_user_id,recorded_by_actor_type,type,channel,subject,content,outcome,visibility,
                        occurred_at,created_at,updated_at)
                    VALUES (?,?,?,?,?, ?,NULL,'SYSTEM','MESSAGE',?,?,?,NULL,'CARE_VISIBLE',?,?,?)
                    """, interactionId, organizationId, profile.customerId(), profile.leadId(), null, conversationId,
                    channelType, "Hội thoại " + channelLabel(channelType), SYSTEM_SUMMARY,
                    Timestamp.from(occurredAt), Timestamp.from(now), Timestamp.from(now));
        }
        jdbc.update("UPDATE conversation_messages SET interaction_id=? WHERE organization_id=? AND conversation_id=? AND id=?",
                interactionId, organizationId, conversationId, messageId);
        return interactionId;
    }

    private static String channelLabel(String channelType) {
        return switch (channelType) {
            case "TELEGRAM" -> "Telegram";
            case "ZALO_SIM" -> "Zalo";
            default -> "kênh";
        };
    }

    private record ConversationProfile(UUID leadId, UUID customerId) {}
    private record SessionMessage(UUID interactionId, Instant occurredAt) {}
}
