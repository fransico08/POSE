package com.pose.backend.ai.eval;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pose.backend.ai.SignalOutputFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public final class EvalDataset {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> INDUSTRIES = Set.of("EDU", "REA", "TECH", "RET", "LOG", "CLI");
    private static final Set<String> FIELDS = Set.of("sampleId", "industry", "split", "text", "goldIntent", "goldSentiment", "annotatorA", "annotatorB");
    public record Annotation(String intent, String sentiment) {}
    public record Sample(String sampleId, String industry, String split, String text, String goldIntent,
                         String goldSentiment, Annotation annotatorA, Annotation annotatorB) {}
    public record Dataset(String sha256, List<Sample> samples) {}
    private EvalDataset() {}

    public static Dataset read(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        String digest;
        try { digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        List<Sample> samples = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        var decoder = StandardCharsets.UTF_8.newDecoder();
        try (var reader = new java.io.BufferedReader(new java.io.StringReader(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()))) {
            String line; int n = 0;
            while ((line = reader.readLine()) != null) {
                n++;
                try {
                    JsonNode row = JSON.readTree(line);
                    if (row == null || !row.isObject()) throw new IllegalArgumentException("expected JSON object");
                    var fields = row.fieldNames();
                    while (fields.hasNext()) if (!FIELDS.contains(fields.next())) throw new IllegalArgumentException("unknown field");
                    String id = required(row,"sampleId"), industry = required(row,"industry"), split = required(row,"split");
                    String text = required(row,"text"), intent = required(row,"goldIntent"), sentiment = required(row,"goldSentiment");
                    if (!id.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("invalid sampleId");
                    if (!ids.add(id)) throw new IllegalArgumentException("duplicate sampleId");
                    if (!INDUSTRIES.contains(industry)) throw new IllegalArgumentException("invalid industry");
                    if (!Set.of("dev","test").contains(split)) throw new IllegalArgumentException("invalid split");
                    if (text.length() > 40000) throw new IllegalArgumentException("text too long");
                    label("INTENT", intent); label("SENTIMENT", sentiment);
                    samples.add(new Sample(id, industry, split, text, intent, sentiment, annotation(row,"annotatorA"), annotation(row,"annotatorB")));
                } catch (Exception e) {
                    String reason = e instanceof IllegalArgumentException ? e.getMessage() : "invalid JSON";
                    throw new IllegalArgumentException("Invalid dataset at line " + n + ": " + reason);
                }
            }
        }
        if (samples.isEmpty()) throw new IllegalArgumentException("Dataset is empty");
        return new Dataset(digest, List.copyOf(samples));
    }
    private static Annotation annotation(JsonNode row,String field) {
        JsonNode node = row.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isObject() || node.size() != 2) throw new IllegalArgumentException("invalid " + field);
        String intent = required(node,"intent"), sentiment = required(node,"sentiment");
        label("INTENT", intent); label("SENTIMENT", sentiment);
        return new Annotation(intent,sentiment);
    }
    private static String required(JsonNode row,String field) {
        JsonNode value = row.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("invalid " + field);
        return value.asText();
    }
    private static void label(String type,String value) {
        if (!SignalOutputFilter.valid(type,value)) throw new IllegalArgumentException("invalid " + type + " label");
    }
}
