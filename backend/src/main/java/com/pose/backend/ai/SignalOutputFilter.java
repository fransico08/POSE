package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SignalOutputFilter {
    private static final Map<String,Set<String>> LABELS=Map.of(
            "INTENT",Set.of("ASK_PRICE","ASK_INFO","BOOKING","PURCHASE_READY","COMPLAINT","SUPPORT","OTHER"),
            "SENTIMENT",Set.of("POSITIVE","NEUTRAL","NEGATIVE"));
    public record Signal(String signalType,String value,double confidence,String evidenceText) {}
    public static boolean valid(String type,String value) {return LABELS.getOrDefault(type,Set.of()).contains(value);}
    public Optional<List<Signal>> filter(String output,String message) {
        if(output==null || message==null)return Optional.empty();
        try {
            JsonNode root=new ObjectMapper().readTree(output), rows=root.path("signals");
            if(!root.isObject()||!rows.isArray())return Optional.empty();
            List<Signal> result=new ArrayList<>(); Set<String> types=new HashSet<>();
            for(JsonNode row:rows) {
                String type=row.path("signalType").asText(), value=row.path("value").asText();
                String evidence=row.path("evidenceText").asText().strip();
                JsonNode confidence=row.path("confidence");
                if(!valid(type,value)||!confidence.isNumber()||!Double.isFinite(confidence.asDouble())||confidence.asDouble()<0||confidence.asDouble()>1
                        || evidence.isEmpty()||!message.contains(evidence)||!types.add(type))continue;
                result.add(new Signal(type,value,confidence.asDouble(),evidence));
            }
            if(!rows.isEmpty() && result.isEmpty())return Optional.empty();
            return Optional.of(List.copyOf(result));
        } catch(Exception ex) {return Optional.empty();}
    }
}
