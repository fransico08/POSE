package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class MockProvider implements AiProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private volatile String lastInput;
    private volatile String lastPrompt;
    private volatile Reply nextReplyOverride;

    public String lastInput() {
        return lastInput;
    }
    public String lastPrompt() { return lastPrompt; }

    public void setNextReply(Reply reply) {
        this.nextReplyOverride = reply;
    }

    @Override
    public Reply generate(Request request) {
        lastInput = request.input();
        lastPrompt = request.prompt();
        Reply override = nextReplyOverride;
        if (override != null) {
            nextReplyOverride = null;
            return override;
        }
        long inputTokens = request.input().length() / 4L + 1;
        String context=AiContextBuilder.unboundContextForMock(request.input());
        if (request.useCase()==AiUseCase.SIGNAL_EXTRACT && request.jsonSchema()!=null) {
            try {
                String message=JSON.readTree(context).path("message").asText();
                return new Reply(JSON.writeValueAsString(Map.of("signals",mockSignals(message))),inputTokens,20,null);
            } catch(Exception ex) {return Reply.failed("PROVIDER_ERROR");}
        }
        if (request.useCase()==AiUseCase.REPLY_DRAFT && request.jsonSchema()!=null) {
            try {
                JsonNode messages=JSON.readTree(context).path("messages");
                if(!messages.isArray() || messages.isEmpty()) return new Reply("{}",inputTokens,20,null);
                String source=messages.get(messages.size()-1).path("source").asText();
                return new Reply(JSON.writeValueAsString(Map.of("draft","Cảm ơn bạn đã liên hệ.","sources",List.of(source))),inputTokens,20,null);
            } catch(Exception ex) {return Reply.failed("PROVIDER_ERROR");}
        }
        if (request.jsonSchema() != null) {
            String structured = buildStructuredCopilotJson(request.useCase(), context);
            return new Reply(structured, inputTokens, 80L, null);
        }
        return new Reply("Gợi ý thử nghiệm: " + context, inputTokens, 20, null);
    }

    private List<Map<String,Object>> mockSignals(String message) {
        String exact=Normalizer.normalize(message,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String plain=Normalizer.normalize(exact,Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d');
        boolean complaint=matches(plain,"khieu nai","chua hai long") || matches(exact,"tệ");
        boolean price=matches(plain,"bao nhieu","hoc phi") || matches(exact,"giá");
        boolean booking=matches(plain,"dat lich","dang ky");
        boolean positive=matches(plain,"cam on") || matches(exact,"tốt");
        String intent=complaint?"COMPLAINT":price?"ASK_PRICE":booking?"BOOKING":"OTHER";
        List<Map<String,Object>> signals=new ArrayList<>();
        signals.add(Map.of("signalType","INTENT","value",intent,"confidence",0.5,"evidenceText",message));
        if(complaint || positive) signals.add(Map.of("signalType","SENTIMENT","value",complaint?"NEGATIVE":"POSITIVE","confidence",0.5,"evidenceText",message));
        return List.copyOf(signals);
    }
    private boolean matches(String text,String... phrases) {
        for(String phrase:phrases) if(Pattern.compile("(?<![\\p{L}\\p{N}_])"+Pattern.quote(phrase)+"(?![\\p{L}\\p{N}_])").matcher(text).find()) return true;
        return false;
    }

    private String buildStructuredCopilotJson(AiUseCase useCase, String input) {
        try {
            JsonNode ctx = JSON.readTree(input);
            String name = ctx.path("name").asText("Khách hàng");
            String status = ctx.path("status").asText("Chưa có dữ liệu");
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
                    String reason = handover.path("reason").asText("Chưa có dữ liệu");
                    String target = handover.path("targetOwnerName").asText("");
                    handoverPoints.add(point("Bàn giao hồ sơ " + name + (target.isBlank() ? "; người nhận: Chưa có dữ liệu" : " cho " + target)
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
                    workPoints.add(point("Chưa có dữ liệu.", List.of("PROFILE")));
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
