package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pose.backend.api.JsonInput;
import com.pose.backend.customer360.Customer360Service;
import com.pose.backend.identity.ActorContext;
import com.pose.backend.lead.LeadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.util.*;

@Component
public class AiContextBuilder {
    private final Customer360Service customers;
    private final LeadService leads;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AiContextBuilder(Customer360Service customers, LeadService leads, ObjectMapper json) {
        this(customers, leads, null, json);
    }

    @Autowired
    public AiContextBuilder(Customer360Service customers, LeadService leads, JdbcTemplate jdbc, ObjectMapper json) {
        this.customers = customers;
        this.leads = leads;
        this.jdbc = jdbc;
        this.json = json;
    }

    public String build(ActorContext actor, String subjectType, UUID subjectId) {
        return build(actor, subjectType, subjectId, AiUseCase.C360_SUMMARY);
    }

    public String build(ActorContext actor, String subjectType, UUID subjectId, AiUseCase useCase) {
        Map<String, Object> data = new LinkedHashMap<>();
        if ("CUSTOMER".equals(subjectType)) {
            var c = customers.profile(actor, subjectId);
            boolean copilot = useCase == AiUseCase.C360_SUMMARY || useCase == AiUseCase.HANDOVER_BRIEF;
            data.put("source", "PROFILE");
            data.put("name", c.customer().profile().displayName());
            data.put("customerType", c.customer().profile().customerType());
            if (copilot) {
                data.put("email", c.customer().profile().email());
                data.put("phone", c.customer().profile().phone());
                if (c.customer().profile().address() != null) {
                    data.put("address", c.customer().profile().address());
                }
            }
            data.put("notes", c.customer().profile().notes());
            data.put("status", c.customer().profile().status());
            if (c.customer().owner() != null) {
                data.put("ownerName", c.customer().owner().name());
            } else if (c.customer().profile().ownerName() != null) {
                data.put("ownerName", c.customer().profile().ownerName());
            }
            if (c.customer().profile().offeringName() != null) {
                data.put("offeringName", c.customer().profile().offeringName());
            }
            if (copilot && !c.opportunities().restricted() && c.opportunities().items() != null) {
                data.put("opportunities", c.opportunities().items().stream().map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("source", "OPPORTUNITY:" + o.id());
                    row.put("title", o.title());
                    row.put("stage", o.stage());
                    row.put("expectedValue", o.expectedValue());
                    row.put("expectedCloseDate", o.expectedCloseDate());
                    return row;
                }).toList());
            }
            List<Map<String, Object>> interactions = c.interactions().items() == null ? List.of()
                    : c.interactions().items().stream().map(i -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        if (i.id() != null) row.put("source", "INTERACTION:" + i.id());
                        row.put("type", i.type());
                        row.put("channel", i.channel());
                        row.put("subject", i.subject());
                        row.put("content", i.content());
                        row.put("outcome", i.outcome());
                        row.put("occurredAt", i.occurredAt());
                        return row;
                    }).toList();
            data.put("interactions", interactions);
            if (copilot && !c.tasks().restricted() && c.tasks().items() != null) {
                data.put("tasks", c.tasks().items().stream().map(t -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("source", "TASK:" + t.id());
                    row.put("title", t.title());
                    row.put("status", t.status());
                    row.put("priority", t.priority());
                    row.put("dueAt", t.dueAt());
                    return row;
                }).toList());
            }
            if (useCase == AiUseCase.HANDOVER_BRIEF) {
                Map<String, Object> handover = loadLatestHandover(actor.organizationId(), subjectId);
                data.put("handover", handover);
                data.put("handoverMode", handover != null ? "APPROVED_HANDOVER" : "PREPARE_HANDOVER");
            }
        } else if ("LEAD".equals(subjectType)) {
            var l = leads.get(actor, subjectId);
            data.put("name", l.displayName());
            if (useCase == AiUseCase.C360_SUMMARY || useCase == AiUseCase.HANDOVER_BRIEF) {
                data.put("email", l.email());
                data.put("phone", l.phone());
            }
            data.put("source", l.source());
            data.put("notes", l.notes());
            data.put("status", l.status());
            data.put("offering", l.offeringName());
        } else {
            throw JsonInput.invalid("subjectType", "Loại hồ sơ không hợp lệ.");
        }
        try {
            return json.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot serialize AI context", ex);
        }
    }

    private Map<String, Object> loadLatestHandover(UUID organizationId, UUID customerId) {
        if (jdbc == null) return null;
        var rows = jdbc.query("""
                SELECT hr.id, hr.reason, hr.reviewed_at,
                       req.full_name AS requested_by_name,
                       tgt.full_name AS target_user_name
                FROM handover_requests hr
                LEFT JOIN users req ON req.id = hr.requested_by_user_id AND req.organization_id = hr.organization_id
                LEFT JOIN users tgt ON tgt.id = hr.target_user_id AND tgt.organization_id = hr.organization_id
                WHERE hr.organization_id = ? AND hr.customer_id = ? AND hr.handover_type = 'SALES' AND hr.status = 'APPROVED'
                ORDER BY hr.reviewed_at DESC, hr.created_at DESC, hr.id
                LIMIT 1
                """, (rs, n) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            UUID id = rs.getObject("id", UUID.class);
            Timestamp reviewed = rs.getTimestamp("reviewed_at");
            map.put("source", "HANDOVER:" + id);
            map.put("reason", rs.getString("reason"));
            map.put("requestedByName", rs.getString("requested_by_name"));
            map.put("targetOwnerName", rs.getString("target_user_name"));
            map.put("reviewedAt", reviewed == null ? null : reviewed.toInstant());
            return map;
        }, organizationId, customerId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public static String truncateJsonElementWise(ObjectMapper mapper, String input, int maxChars) {
        if (input == null || input.length() <= maxChars) return input;
        if (maxChars <= 2) return "{}".substring(0, Math.max(0, maxChars));
        try {
            JsonNode parsed = mapper.readTree(input);
            if (!(parsed instanceof ObjectNode root)) {
                return input.substring(0, maxChars);
            }
            String[] arrayKeys = {"tasks", "opportunities", "interactions"};
            while (mapper.writeValueAsString(root).length() > maxChars) {
                boolean removedAny = false;
                for (String key : arrayKeys) {
                    JsonNode node = root.get(key);
                    if (node instanceof ArrayNode arr && !arr.isEmpty()) {
                        arr.remove(arr.size() - 1);
                        removedAny = true;
                        if (mapper.writeValueAsString(root).length() <= maxChars) {
                            return mapper.writeValueAsString(root);
                        }
                    }
                }
                if (!removedAny) break;
            }
            String[] optionalFields = {"notes", "address", "handover", "tasks", "opportunities", "interactions", "offeringName", "ownerName", "email", "phone", "customerType", "status"};
            for (String field : optionalFields) {
                if (mapper.writeValueAsString(root).length() <= maxChars) break;
                root.remove(field);
            }
            if (mapper.writeValueAsString(root).length() > maxChars && root.has("name")) {
                String name = root.path("name").asText("");
                int excess = mapper.writeValueAsString(root).length() - maxChars;
                int keep = Math.max(0, name.length() - excess - 3);
                if (keep > 0) {
                    root.put("name", name.substring(0, keep));
                } else {
                    root.remove("name");
                }
            }
            String candidate = mapper.writeValueAsString(root);
            return candidate.length() <= maxChars ? candidate : "{}";
        } catch (Exception ex) {
            return input.substring(0, maxChars);
        }
    }

    public static Set<String> extractValidSources(ObjectMapper mapper, String contextJson) {
        Set<String> sources = new LinkedHashSet<>();
        if (contextJson == null || contextJson.isBlank()) return sources;
        try {
            JsonNode root = mapper.readTree(contextJson);
            collectSources(root, sources);
        } catch (Exception ignored) {
        }
        return sources;
    }

    private static void collectSources(JsonNode node, Set<String> out) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            JsonNode src = node.get("source");
            if (src != null && src.isTextual() && !src.asText().isBlank()) {
                String val = src.asText().strip();
                if (val.equals("PROFILE") || val.startsWith("INTERACTION:") || val.startsWith("TASK:")
                        || val.startsWith("OPPORTUNITY:") || val.startsWith("HANDOVER:")) {
                    out.add(val);
                }
            }
            node.fields().forEachRemaining(entry -> collectSources(entry.getValue(), out));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectSources(item, out);
            }
        }
    }
}
