package com.pose.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Examines provider output without retaining or logging offending text. */
final class AiOutputSafety {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern UUID = Pattern.compile("(?i)(?<![0-9a-f])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f])");
    private static final Pattern URL = Pattern.compile("(?i)(?<![@\\p{L}\\p{N}_-])(?:[a-z][a-z0-9+.-]{1,15}://|www\\.)[^\\s<>'\"\\[\\]{}]+|(?<![@\\p{L}\\p{N}_-])(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(?:/[^\\s<>'\"\\[\\]{}]*)?");
    private final Redactor redactor;
    AiOutputSafety(Redactor redactor) { this.redactor=redactor; }

    String violation(String output, String context, String prompt) {
        if(output==null) return null;
        List<String> values=textValues(output), contextValues=textValues(context);
        String contextText=String.join("\n",contextValues);
        String contextIds=contextText.toLowerCase(Locale.ROOT);
        for(String value:values) if(leaksPrompt(value,prompt)) return "SYSTEM_PROMPT_LEAK";
        for(String value:values) {
            var urls=URL.matcher(value);
            while(urls.find()) {
                String url=urls.group().replaceFirst("[.,;:!?)]+$","");
                if(!contextText.contains(url)) return "UNKNOWN_URL";
            }
        }
        for(String value:values) {
            var ids=UUID.matcher(value);
            while(ids.find()) if(!contextIds.contains(ids.group().toLowerCase(Locale.ROOT))) return "UNKNOWN_UUID";
        }
        for(String value:values) if(redactor.containsContact(UUID.matcher(value).replaceAll(" "))) return "CONTACT_INFO";
        return null;
    }
    private static boolean leaksPrompt(String value,String prompt) {
        if(prompt==null) return false;
        String normalized=normalize(value);
        for(String clause:prompt.split("(?<=[.!?;])\\s+")) {
            String protectedText=normalize(clause).replaceAll("[.!?;]+$","").strip();
            if(protectedText.length()>=30 && normalized.contains(protectedText)) return true;
            String[] words=protectedText.split(" ");
            for(int start=0;start+8<=words.length;start++) {
                String fragment=String.join(" ",java.util.Arrays.copyOfRange(words,start,start+8));
                if(fragment.length()>=35 && normalized.contains(fragment)) return true;
            }
        }
        return false;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").strip(); }
    private static List<String> textValues(String text) {
        if(text==null) return List.of();
        try { var values=new ArrayList<String>(); collect(JSON.readTree(text),values); return values; }
        catch(Exception ignored) { return List.of(text); }
    }
    private static void collect(JsonNode node,List<String> values) {
        if(node==null) return;
        if(node.isTextual()) values.add(node.asText());
        else if(node.isObject()) node.fields().forEachRemaining(entry->{values.add(entry.getKey());collect(entry.getValue(),values);});
        else if(node.isArray()) node.elements().forEachRemaining(child->collect(child,values));
    }
}
