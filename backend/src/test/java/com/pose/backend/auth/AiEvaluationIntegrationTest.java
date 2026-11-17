package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.pose.backend.ai.*;
import com.pose.backend.ai.eval.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties={"pose.ai.enabled=true","pose.ai.provider=mock","pose.sales-assignment.expiry-scan-delay-ms=86400000"})
class AiEvaluationIntegrationTest extends FoundationTestSupport {
    @Autowired AiEvaluation evaluation;
    @Autowired MockProvider mock;
    @Autowired(required=false) AiEvaluationRunner runner;
    @TempDir Path output;

    private AiEvaluation.Config config(UUID id) throws Exception {
        Path fixture=Path.of(Objects.requireNonNull(getClass().getResource("/ai-eval/synthetic.jsonl")).toURI());
        return new AiEvaluation.Config(fixture,"test",20,0,1,output,id,"synthetic-test-commit",false);
    }
    @Test void runsGatewayWithoutPersistingSignalsOrMessageTextAndReportsOnlyOwnInvocations() throws Exception {
        UUID run=UUID.randomUUID();
        assertThat(runner).isNull();
        int tasks=jdbc.queryForObject("SELECT count(*) FROM crm_tasks",Integer.class);
        int signals=jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class);
        int suggestions=jdbc.queryForObject("SELECT count(*) FROM ai_suggestions",Integer.class);
        Path dir=evaluation.run(config(run));
        JsonNode manifest=mapper.readTree(Files.readString(dir.resolve("run.json")));
        assertThat(manifest.path("sampleCount").asInt()).isEqualTo(3);
        assertThat(manifest.path("complete").asBoolean()).isTrue();
        assertThat(manifest.path("datasetSha256").asText()).hasSize(64);
        assertThat(manifest.path("promptVersion").asText()).contains("signal-extract");
        JsonNode metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("scoredSamples").asInt()).isEqualTo(3);
        assertThat(metrics.path("intent").path("coverage").asDouble()).isEqualTo(1);
        assertThat(metrics.path("sentiment").path("coverage").asDouble()).isZero();
        assertThat(metrics.path("annotationPairs").asInt()).isEqualTo(2);
        assertThat(metrics.path("performance").path("totalTokens").asLong()).isEqualTo(metrics.path("performance").path("inputTokens").asLong()+metrics.path("performance").path("outputTokens").asLong());
        assertThat(metrics.path("industries").path("EDU").path("status").asText()).isEqualTo("INSUFFICIENT_SAMPLES");
        var rows=jdbc.queryForList("SELECT cost_estimate,latency_ms,actor_type,actor_user_id FROM ai_invocations WHERE organization_id=? AND subject_id=? ORDER BY latency_ms",run,run);
        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> { assertThat(row.get("actor_type")).isEqualTo("SYSTEM"); assertThat(row.get("actor_user_id")).isNull(); });
        assertThat(metrics.path("performance").path("latencyP95Ms").asLong()).isEqualTo(((Number)rows.getLast().get("latency_ms")).longValue());
        BigDecimal cost=jdbc.queryForObject("SELECT SUM(cost_estimate) FROM ai_invocations WHERE organization_id=? AND subject_id=?",BigDecimal.class,run,run);
        assertThat(metrics.path("performance").path("costEstimateUsd").decimalValue()).isEqualByComparingTo(cost);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM crm_tasks",Integer.class)).isEqualTo(tasks);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_signals",Integer.class)).isEqualTo(signals);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_suggestions",Integer.class)).isEqualTo(suggestions);
        for (String name : List.of("run.json","metrics.json","report.md","per-sample.jsonl"))
            assertThat(Files.readString(dir.resolve(name),StandardCharsets.UTF_8)).doesNotContain("Tôi muốn", "Xin thêm", "tư vấn thêm");
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).hasSize(3);
    }
    @Test void resumesAfterRateLimitWithoutRepeatingSamples() throws Exception {
        UUID run=UUID.randomUUID();var config=config(run);
        mock.setNextReply(AiProvider.Reply.failed("RATE_LIMIT"));
        Path dir=evaluation.run(config);
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).hasSize(1);
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("complete").asBoolean()).isFalse();
        evaluation.run(config);
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=? AND subject_id=?",Integer.class,run,run)).isEqualTo(3);
        assertThat(mapper.readTree(Files.readString(dir.resolve("metrics.json"))).path("errors").path("RATE_LIMIT").asInt()).isEqualTo(1);
    }
    @Test void invalidDatasetDoesNotCallProviderOrCreateOrganization() throws Exception {
        UUID run=UUID.randomUUID();var good=config(run);Path invalid=output.resolve("invalid.jsonl");
        Files.writeString(invalid,"{\"sampleId\":\"bad\"}\n",StandardCharsets.UTF_8);
        var config=new AiEvaluation.Config(invalid,good.split(),good.maxSamples(),good.delayMs(),good.maxConsecutiveErrors(),good.outputRoot(),run,good.gitCommit(),good.gitDirty());
        assertThatThrownBy(() -> evaluation.run(config)).hasMessageContaining("line 1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizations WHERE id=?",Integer.class,run)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isZero();
    }
    @Test void recoversCommittedInvocationWithLostCheckpointWithoutCallingAgain() throws Exception {
        UUID run=UUID.randomUUID();var config=config(run);Path dir=evaluation.run(config);
        var lines=Files.readAllLines(dir.resolve("per-sample.jsonl"));
        Files.write(dir.resolve("per-sample.jsonl"),lines.subList(0,2),StandardCharsets.UTF_8);
        evaluation.run(config);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=? AND subject_id=?",Integer.class,run,run)).isEqualTo(3);
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).contains("CHECKPOINT_LOST");
    }
    @Test void reportsSafetyRejectionWithoutPersistingItsContent() throws Exception {
        UUID run=UUID.randomUUID();
        mock.setNextReply(new AiProvider.Reply("{\"signals\":[],\"note\":\"https://unknown.example/x\"}",5,5,null));
        Path dir=evaluation.run(config(run));
        var metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("errors").path("INVALID_OUTPUT").asInt()).isEqualTo(1);
        assertThat(metrics.path("errorDetails").path("UNKNOWN_URL").asInt()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).doesNotContain("unknown.example");
    }
}
