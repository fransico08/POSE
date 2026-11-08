package com.pose.backend.ai;

import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Locale;

@Component
public class Redactor {
    private static final Pattern EMAIL=Pattern.compile("(?i)(?<![\\w.+-])[\\w.+-]+@[a-z0-9.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern PHONE=Pattern.compile("(?<![\\d+])(?:\\+84[ .-]*|0)[1-9](?:[ .-]*\\d){8}(?![\\d])");
    private static final Pattern IDENTITY=Pattern.compile("(?<![\\da-zA-Z-])(?:\\d{12}|\\d{9})(?![\\da-zA-Z-])");
    public record Result(String text,int count) {}
    public Result redact(String input) {
        int[] counters={0,0,0}; String text=redactText(input,counters);
        return new Result(text,counters[0]+counters[1]+counters[2]);
    }
    public Result redactContext(String input) {
        try {
            ObjectMapper mapper=new ObjectMapper(); int[] counters={0,0,0}; JsonNode tree=mapper.readTree(input);
            if(tree==null || !tree.isObject()) throw new IllegalArgumentException("Expected JSON context");
            redactNode(tree,counters);
            return new Result(mapper.writeValueAsString(tree),counters[0]+counters[1]+counters[2]);
        } catch(Exception ex) {throw new IllegalArgumentException("Invalid JSON context",ex);}
    }
    private void redactNode(JsonNode node,int[] counters) {
        if(node instanceof ObjectNode object) {
            object.fields().forEachRemaining(entry->{String original=entry.getKey(),key=original.toLowerCase(Locale.ROOT); JsonNode value=entry.getValue();
                if(key.equals("source") || key.equals("id") || original.endsWith("Id") || original.endsWith("Ids") || key.endsWith("_id") || key.endsWith("_ids")) return;
                if(value.isTextual()) object.set(entry.getKey(),TextNode.valueOf(redactText(value.asText(),counters)));
                else redactNode(value,counters);
            });
        } else if(node instanceof ArrayNode array) {
            for(int i=0;i<array.size();i++) {JsonNode value=array.get(i);if(value.isTextual()) array.set(i,TextNode.valueOf(redactText(value.asText(),counters)));else redactNode(value,counters);}
        }
    }
    private String redactText(String input,int[] counters) {
        String text=replace(EMAIL,input,"EMAIL",counters,0);text=replace(PHONE,text,"PHONE",counters,1);
        return replace(IDENTITY,text,"IDENTITY",counters,2);
    }
    private String replace(Pattern pattern,String input,String label,int[] counters,int slot) {
        Matcher m=pattern.matcher(input); StringBuffer result=new StringBuffer();
        while(m.find()) {counters[slot]++;m.appendReplacement(result,Matcher.quoteReplacement("["+label+"_"+counters[slot]+"]"));}
        m.appendTail(result);return result.toString();
    }
}
