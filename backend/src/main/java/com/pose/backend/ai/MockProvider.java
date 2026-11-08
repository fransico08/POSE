package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MockProvider implements AiProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private volatile String lastInput;
    private volatile Reply nextReplyOverride;

    public String lastInput() {
        return lastInput;
    }

    public void setNextReply(Reply reply) {
        this.nextReplyOverride = reply;
    }

    @Override
    public Reply generate(Request request) {
        lastInput = request.input();
        Reply override = nextReplyOverride;
        if (override != null) {
            nextReplyOverride = null;
            return override;
        }
        long inputTokens = request.input().length() / 4L + 1;
        if (request.jsonSchema() != null) {
            String structured = buildStructuredCopilotJson(request.useCase(), request.input());
            return new Reply(structured, inputTokens, 80L, null);
        }
        return new Reply("Gợi ý thử nghiệm: " + request.input(), inputTokens, 20, null);
    }

    private String buildStructuredCopilotJson(AiUseCase useCase, String input) {
        try {
            JsonNode ctx = JSON.readTree(input);
            String name = ctx.path("name").asText("Khách hàng");
            String status = ctx.path("status").asText("ACTIVE");
            String offering = ctx.path("offeringName").asText("");
            String owner = ctx.path("ownerName").asText("");

            List<String> oppSources = new ArrayList<>();
            List<String> oppTitles = new ArrayList<>();
            if (ctx.has("opportunities") && ctx.get("opportunities").isArray()) {
                for (JsonNode n : ctx.get("opportunities")) {
                    String src = n.path("source").asText("");
                    if (!src.isBlank()) {
                        oppSources.add(src);
                        oppTitles.add(n.path("title").asText("Cơ hội"));
                    }
                }
            }
            List<String> intSources = new ArrayList<>();
            List<String> intSubjects = new ArrayList<>();
            if (ctx.has("interactions") && ctx.get("interactions").isArray()) {
                for (JsonNode n : ctx.get("interactions")) {
                    String src = n.path("source").asText("");
                    if (!src.isBlank()) {
                        intSources.add(src);
                        intSubjects.add(n.path("subject").asText("Tương tác"));
                    }
                }
            }
            List<String> taskSources = new ArrayList<>();
            List<String> taskTitles = new ArrayList<>();
            if (ctx.has("tasks") && ctx.get("tasks").isArray()) {
                for (JsonNode n : ctx.get("tasks")) {
                    String src = n.path("source").asText("");
                    if (!src.isBlank()) {
                        taskSources.add(src);
                        taskTitles.add(n.path("title").asText("Nhiệm vụ"));
                    }
                }
            }

            List<Map<String, Object>> sections = new ArrayList<>();
            if (useCase == AiUseCase.HANDOVER_BRIEF) {
                JsonNode handover = ctx.get("handover");
                List<Map<String, Object>> handoverPoints = new ArrayList<>();
                if (handover != null && handover.isObject() && !handover.path("source").asText("").isBlank()) {
                    String hSource = handover.path("source").asText();
                    String reason = handover.path("reason").asText("Điều phối phụ trách");
                    String target = handover.path("targetOwnerName").asText(owner);
                    handoverPoints.add(point("Bàn giao hồ sơ " + name + " cho " + (target.isBlank() ? "người phụ trách mới" : target)
                            + " với lý do: " + reason + ".", List.of(hSource, "PROFILE")));
                } else {
                    handoverPoints.add(point("Chuẩn bị bàn giao hồ sơ " + name + " (trạng thái " + status
                            + (offering.isBlank() ? "" : ", sản phẩm liên quan: " + offering) + ").", List.of("PROFILE")));
                }
                sections.add(section("Thông tin bàn giao", handoverPoints));

                List<Map<String, Object>> workPoints = new ArrayList<>();
                if (!oppSources.isEmpty()) {
                    workPoints.add(point("Cơ hội cần tiếp nhận: " + oppTitles.getFirst() + ".", List.of(oppSources.getFirst())));
                }
                if (!taskSources.isEmpty()) {
                    workPoints.add(point("Nhiệm vụ cần xử lý tiếp: " + taskTitles.getFirst() + ".", List.of(taskSources.getFirst())));
                }
                if (!intSources.isEmpty()) {
                    workPoints.add(point("Trao đổi gần nhất: " + intSubjects.getFirst() + ".", List.of(intSources.getFirst())));
                }
                if (workPoints.isEmpty()) {
                    workPoints.add(point("Hiện chưa có cơ hội hoặc nhiệm vụ đang mở; cần liên hệ làm quen với khách hàng.", List.of("PROFILE")));
                }
                sections.add(section("Công việc và ngữ cảnh tiếp nhận", workPoints));
            } else {
                List<Map<String, Object>> overviewPoints = new ArrayList<>();
                overviewPoints.add(point("Hồ sơ " + name + " đang ở trạng thái " + status
                        + (offering.isBlank() ? "" : ", quan tâm tới " + offering)
                        + (owner.isBlank() ? "." : ", do " + owner + " phụ trách."), List.of("PROFILE")));
                sections.add(section("Tổng quan hồ sơ", overviewPoints));

                List<Map<String, Object>> activityPoints = new ArrayList<>();
                if (!oppSources.isEmpty()) {
                    activityPoints.add(point("Cơ hội nổi bật: " + oppTitles.getFirst() + ".", List.of(oppSources.getFirst())));
                }
                if (!intSources.isEmpty()) {
                    activityPoints.add(point("Tương tác gần đây: " + intSubjects.getFirst() + ".", List.of(intSources.getFirst())));
                }
                if (!taskSources.isEmpty()) {
                    activityPoints.add(point("Nhiệm vụ cần theo dõi: " + taskTitles.getFirst() + ".", List.of(taskSources.getFirst())));
                }
                if (!activityPoints.isEmpty()) {
                    sections.add(section("Hoạt động và công việc liên quan", activityPoints));
                }
            }
            return JSON.writeValueAsString(Map.of("sections", sections));
        } catch (Exception ex) {
            return "{\"sections\":[{\"heading\":\"Tổng quan hồ sơ\",\"points\":[{\"text\":\"Tóm tắt thông tin hồ sơ khách hàng.\",\"sources\":[\"PROFILE\"]}]}]}";
        }
    }

    private static Map<String, Object> section(String heading, List<Map<String, Object>> points) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("heading", heading);
        map.put("points", points);
        return map;
    }

    private static Map<String, Object> point(String text, List<String> sources) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", text);
        map.put("sources", sources);
        return map;
    }
}
