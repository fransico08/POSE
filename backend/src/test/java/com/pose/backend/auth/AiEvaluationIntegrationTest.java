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
    @Autowired AiSettings settings;
    @Autowired PromptRegistry prompts;
    @Autowired(required=false) AiEvaluationRunner runner;
    @TempDir Path output;

    private AiEvaluation.Config config(UUID id) throws Exception {
        Path fixture=Path.of(Objects.requireNonNull(getClass().getResource("/ai-eval/synthetic.jsonl")).toURI());
        return new AiEvaluation.Config(fixture,"test",20,0,1,3,output,id,"synthetic-test-commit",false);
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
        UUID baseline=UUID.randomUUID();Path base=evaluation.run(config(baseline));
        double expected=mapper.readTree(Files.readString(base.resolve("metrics.json"))).path("intent").path("macroF1").asDouble();
        UUID run=UUID.randomUUID();var config=config(run);
        mock.setNextReply(AiProvider.Reply.failed("RATE_LIMIT"));
        Path dir=evaluation.run(config);
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).isEmpty();
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("complete").asBoolean()).isFalse();
        assertThat(Files.readString(dir.resolve("report.md"))).startsWith("KẾT QUẢ TẠM — chưa đủ mẫu");
        evaluation.run(config);
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=? AND subject_id=?",Integer.class,run,run)).isEqualTo(4);
        var metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("errors").has("RATE_LIMIT")).isFalse();
        assertThat(metrics.path("infrastructure").path("attemptErrors").path("RATE_LIMIT").asInt()).isEqualTo(1);
        assertThat(metrics.path("intent").path("macroF1").asDouble()).isEqualTo(expected);
        jdbc.update("UPDATE ai_invocations SET latency_ms=999999,cost_estimate=1 WHERE organization_id=? AND status='FAILED'",run);
        evaluation.run(config);
        metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        var sql=jdbc.queryForList("SELECT latency_ms FROM ai_invocations WHERE organization_id=? AND status='SUCCEEDED' ORDER BY latency_ms",Long.class,run);
        assertThat(metrics.path("performance").path("latencyP95Ms").asLong()).isEqualTo(sql.getLast());
        assertThat(metrics.path("performance").path("latencyInvocationCount").asInt()).isEqualTo(3);
        assertThat(metrics.path("performance").path("costEstimateUsd").decimalValue()).isEqualByComparingTo(jdbc.queryForObject("SELECT SUM(cost_estimate) FROM ai_invocations WHERE organization_id=?",BigDecimal.class,run));
    }
    @Test void invalidDatasetDoesNotCallProviderOrCreateOrganization() throws Exception {
        UUID run=UUID.randomUUID();var good=config(run);Path invalid=output.resolve("invalid.jsonl");
        Files.writeString(invalid,"{\"sampleId\":\"bad\"}\n",StandardCharsets.UTF_8);
        var config=new AiEvaluation.Config(invalid,good.split(),good.maxSamples(),good.delayMs(),good.maxConsecutiveErrors(),good.maxAttempts(),good.outputRoot(),run,good.gitCommit(),good.gitDirty());
        assertThatThrownBy(() -> evaluation.run(config)).hasMessageContaining("line 1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizations WHERE id=?",Integer.class,run)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isZero();
    }
    @Test void recoversCommittedInvocationWithLostCheckpointByCallingAgain() throws Exception {
        UUID run=UUID.randomUUID();var config=config(run);Path dir=evaluation.run(config);
        var lines=Files.readAllLines(dir.resolve("per-sample.jsonl"));
        Files.write(dir.resolve("per-sample.jsonl"),lines.subList(0,2),StandardCharsets.UTF_8);
        evaluation.run(config);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=? AND subject_id=?",Integer.class,run,run)).isEqualTo(4);
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).doesNotContain("CHECKPOINT_LOST");
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("recoveredSuccessWithoutCheckpoint").asInt()).isEqualTo(1);
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("replayedAfterLostCheckpoint").asInt()).isEqualTo(1);
    }
    @Test void lostSuccessfulCheckpointAtLimitIsExcludedWithoutExceedingAttemptLimit() throws Exception {
        UUID run=UUID.randomUUID();var base=config(run);
        var config=new AiEvaluation.Config(base.dataset(),base.split(),base.maxSamples(),0,1,1,output,run,base.gitCommit(),base.gitDirty());
        Path dir=evaluation.run(config);var lines=Files.readAllLines(dir.resolve("per-sample.jsonl"));
        Files.write(dir.resolve("per-sample.jsonl"),lines.subList(0,2),StandardCharsets.UTF_8);
        evaluation.run(config);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isEqualTo(3);
        var metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("scoredSamples").asInt()).isEqualTo(2);
        assertThat(metrics.path("infrastructure").path("exhaustedSampleIds").toString()).contains("SYN-03");
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).contains("CHECKPOINT_LOST","EXHAUSTED_RETRIES");
    }
    @Test void reportsSafetyRejectionWithoutPersistingItsContent() throws Exception {
        UUID run=UUID.randomUUID();
        mock.setNextReply(new AiProvider.Reply("{\"signals\":[],\"note\":\"https://unknown.example/x\"}",5,5,null));
        Path dir=evaluation.run(config(run));
        var metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("errors").path("INVALID_OUTPUT").asInt()).isEqualTo(1);
        assertThat(metrics.path("errorDetails").path("UNKNOWN_URL").asInt()).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).doesNotContain("unknown.example");
        evaluation.run(config(run));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isEqualTo(3);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"RATE_LIMIT","TIMEOUT"})
    void transientProviderErrorsStopBatchAndReportTemporary(String error) throws Exception {
        UUID run=UUID.randomUUID();mock.setNextReply(AiProvider.Reply.failed(error));
        Path dir=evaluation.run(config(run));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isEqualTo(1);
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("complete").asBoolean()).isFalse();
        assertThat(Files.readString(dir.resolve("report.md"))).startsWith("KẾT QUẢ TẠM — chưa đủ mẫu");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"RATE_LIMIT","TIMEOUT"})
    void consecutiveProviderFailuresStopAtConfiguredThreshold(String error) throws Exception {
        UUID run=UUID.randomUUID();var base=config(run);var fake=org.mockito.Mockito.mock(AiGateway.class);
        org.mockito.Mockito.when(fake.invokeEvaluationSignal(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(a -> new AiGateway.Result(a.getArgument(0),"FAILED",error,null,false));
        var subject=new AiEvaluation(fake,settings,prompts,jdbc,clock);
        var config=new AiEvaluation.Config(base.dataset(),base.split(),base.maxSamples(),0,2,3,output,run,base.gitCommit(),base.gitDirty());
        Path dir=subject.run(config);
        org.mockito.Mockito.verify(fake,org.mockito.Mockito.times(2)).invokeEvaluationSignal(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap());
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("complete").asBoolean()).isFalse();
        assertThat(Files.readString(dir.resolve("report.md"))).startsWith("KẾT QUẢ TẠM — chưa đủ mẫu");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"DISABLED","BLOCKED_BUDGET"})
    void disabledOrBlockedStopsWithoutFinalizingSample(String status) throws Exception {
        UUID run=UUID.randomUUID();var base=config(run);var fake=org.mockito.Mockito.mock(AiGateway.class);
        org.mockito.Mockito.when(fake.invokeEvaluationSignal(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(a -> new AiGateway.Result(a.getArgument(0),status,null,null,false));
        var subject=new AiEvaluation(fake,settings,prompts,jdbc,clock);Path dir=subject.run(base);
        org.mockito.Mockito.verify(fake,org.mockito.Mockito.times(1)).invokeEvaluationSignal(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap());
        assertThat(Files.readAllLines(dir.resolve("per-sample.jsonl"))).isEmpty();
        assertThat(mapper.readTree(Files.readString(dir.resolve("metrics.json"))).path("infrastructure").path("attemptErrors").path(status).asInt()).isEqualTo(1);
        assertThat(mapper.readTree(Files.readString(dir.resolve("metrics.json"))).path("intent").isNull()).isTrue();
    }
    @Test void badRequestIsFinalAndIsNotRetried() throws Exception {
        UUID run=UUID.randomUUID();mock.setNextReply(AiProvider.Reply.failed("BAD_REQUEST"));Path dir=evaluation.run(config(run));
        evaluation.run(config(run));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isEqualTo(3);
        assertThat(mapper.readTree(Files.readString(dir.resolve("metrics.json"))).path("errors").path("BAD_REQUEST").asInt()).isEqualTo(1);
    }
    @Test void resumesLegacyTransientCheckpointWithOriginalCommitRecorded() throws Exception {
        UUID run=UUID.randomUUID();var config=config(run);mock.setNextReply(AiProvider.Reply.failed("RATE_LIMIT"));Path dir=evaluation.run(config);
        UUID legacy=UUID.nameUUIDFromBytes((run+":SYN-01").getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE ai_invocations SET id=? WHERE organization_id=?",legacy,run);
        Files.delete(dir.resolve("attempts.jsonl"));
        var manifest=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Files.readString(dir.resolve("run.json")));
        manifest.remove("maxAttempts");manifest.put("gitCommit","previous-commit");Files.writeString(dir.resolve("run.json"),mapper.writeValueAsString(manifest));
        Files.writeString(dir.resolve("per-sample.jsonl"),"{\"sampleId\":\"SYN-01\",\"goldIntent\":\"ASK_PRICE\",\"goldSentiment\":\"NEUTRAL\",\"predictedIntent\":\"NO_PREDICTION\",\"predictedSentiment\":\"NO_PREDICTION\",\"errorCode\":\"RATE_LIMIT\",\"errorDetail\":null}\n");
        evaluation.run(config);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_invocations WHERE organization_id=?",Integer.class,run)).isEqualTo(4);
        assertThat(mapper.readTree(Files.readString(dir.resolve("run.json"))).path("originalGitCommit").asText()).isEqualTo("previous-commit");
        assertThat(mapper.readTree(Files.readString(dir.resolve("metrics.json"))).path("errors").has("RATE_LIMIT")).isFalse();
    }
    @Test void exhaustedTransientSampleIsExcludedFromModelMetrics() throws Exception {
        UUID run=UUID.randomUUID();var base=config(run);
        var config=new AiEvaluation.Config(base.dataset(),base.split(),base.maxSamples(),0,1,2,output,run,base.gitCommit(),base.gitDirty());
        mock.setNextReply(AiProvider.Reply.failed("NETWORK"));Path dir=evaluation.run(config);
        mock.setNextReply(AiProvider.Reply.failed("NETWORK"));evaluation.run(config);
        evaluation.run(config);
        var metrics=mapper.readTree(Files.readString(dir.resolve("metrics.json")));
        assertThat(metrics.path("complete").asBoolean()).isTrue();
        assertThat(metrics.path("scoredSamples").asInt()).isEqualTo(2);
        assertThat(metrics.path("infrastructure").path("exhaustedCount").asInt()).isEqualTo(1);
        assertThat(metrics.path("infrastructure").path("exhaustedSampleIds").toString()).contains("SYN-01");
        assertThat(Files.readString(dir.resolve("per-sample.jsonl"))).contains("EXHAUSTED_RETRIES");
    }
}
