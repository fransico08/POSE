package com.pose.backend.ai.eval;

import java.util.regex.Pattern;

/** Evaluations can start only on an explicitly isolated database. */
public final class EvalDatasourceGuard {
    private static final Pattern POSTGRES = Pattern.compile("jdbc:postgresql://127\\.0\\.0\\.1:54329/pose_(?:ai_eval|foundation_test)_[a-z0-9_]+");
    private static final Pattern H2 = Pattern.compile("jdbc:h2:mem:[a-zA-Z0-9_-]+(?:;[a-zA-Z0-9_=-]+)*");
    private EvalDatasourceGuard() {}
    public static void validate(String url) {
        if (url == null || !(POSTGRES.matcher(url).matches() || H2.matcher(url).matches()))
            throw new IllegalArgumentException("AI evaluation requires an isolated in-memory H2 or loopback PostgreSQL 54329 evaluation database");
    }
    public static void validateOverrides(String url,String flywayUrl,String hikariUrl) {
        validate(url);
        for (String override : new String[]{flywayUrl,hikariUrl}) {
            if (override != null && !override.isBlank() && !override.equals(url))
                throw new IllegalArgumentException("AI evaluation datasource overrides must match the isolated datasource");
        }
    }
}
