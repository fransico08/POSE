package com.pose.backend.ai.eval;

import java.util.*;

/** Classification scores; missing predictions are errors, not an extra gold class. */
public final class EvalMetrics {
    public static final String NO_PREDICTION = "NO_PREDICTION";
    public record Pair(String gold, String predicted) {}
    public record ClassScore(long support, long predicted, long truePositive, double precision, double recall, double f1) {}
    public record Result(int samples, double accuracy, double coverage, double macroF1,
                         Map<String,ClassScore> classes, Map<String,Map<String,Long>> confusion) {}

    private EvalMetrics() {}

    public static Result calculate(List<Pair> pairs) {
        var labels = new TreeSet<String>();
        for (var pair : pairs) labels.add(pair.gold());
        var columns = new TreeSet<>(labels);
        for (var pair : pairs) if (!NO_PREDICTION.equals(pair.predicted())) columns.add(pair.predicted());
        columns.add(NO_PREDICTION);
        var confusion = new LinkedHashMap<String,Map<String,Long>>();
        var classes = new LinkedHashMap<String,ClassScore>();
        long correct = 0, covered = 0;
        for (var gold : labels) {
            var row = new LinkedHashMap<String,Long>();
            for (var predicted : columns) row.put(predicted, 0L);
            confusion.put(gold, row);
        }
        for (var pair : pairs) {
            confusion.get(pair.gold()).compute(pair.predicted(), (key, count) -> count + 1);
            if (pair.gold().equals(pair.predicted())) correct++;
            if (!NO_PREDICTION.equals(pair.predicted())) covered++;
        }
        double sum = 0;
        var scoredLabels = new TreeSet<>(columns);
        scoredLabels.remove(NO_PREDICTION);
        for (var label : scoredLabels) {
            long support = pairs.stream().filter(p -> p.gold().equals(label)).count();
            long predicted = pairs.stream().filter(p -> p.predicted().equals(label)).count();
            long tp = labels.contains(label) ? confusion.get(label).get(label) : 0;
            double precision = predicted == 0 ? 0 : (double)tp / predicted;
            double recall = support == 0 ? 0 : (double)tp / support;
            double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
            classes.put(label, new ClassScore(support, predicted, tp, precision, recall, f1));
            if (support > 0) sum += f1;
        }
        return new Result(pairs.size(), pairs.isEmpty() ? 0 : (double)correct / pairs.size(),
                pairs.isEmpty() ? 0 : (double)covered / pairs.size(), labels.isEmpty() ? 0 : sum / labels.size(), classes, confusion);
    }

    /** Returns null when kappa is undefined (no pairs or perfect expected agreement). */
    public static Double kappa(List<Pair> pairs) {
        if (pairs.isEmpty()) return null;
        var labels = new HashSet<String>();
        pairs.forEach(pair -> { labels.add(pair.gold()); labels.add(pair.predicted()); });
        long agree = pairs.stream().filter(pair -> pair.gold().equals(pair.predicted())).count();
        double expected = 0;
        for (String label : labels) {
            long a = pairs.stream().filter(p -> p.gold().equals(label)).count();
            long b = pairs.stream().filter(p -> p.predicted().equals(label)).count();
            expected += (double)a * b / pairs.size() / pairs.size();
        }
        return expected == 1 ? null : ((double)agree / pairs.size() - expected) / (1 - expected);
    }
}
