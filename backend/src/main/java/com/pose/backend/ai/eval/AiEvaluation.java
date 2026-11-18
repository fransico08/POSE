package com.pose.backend.ai.eval;

import com.fasterxml.jackson.databind.*;
import com.pose.backend.ai.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class AiEvaluation {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Set<String> STOP_ERRORS = Set.of("RATE_LIMIT", "NETWORK", "PROVIDER_ERROR", "TIMEOUT");
    private static final Set<String> FINAL_ERRORS = Set.of("INVALID_OUTPUT", "BAD_REQUEST");
    public record Config(Path dataset, String split, int maxSamples, long delayMs, int maxConsecutiveErrors,
                         int maxAttempts, Path outputRoot, UUID runId, String gitCommit, boolean gitDirty) {}
    public record SampleResult(String sampleId, String goldIntent, String goldSentiment, String predictedIntent,
                               String predictedSentiment, String errorCode, String errorDetail, String disposition) {}
    public record Attempt(String sampleId, int attempt, UUID invocationId, String status, String errorCode, String errorDetail) {}
    private final AiGateway gateway;
    private final AiSettings settings;
    private final PromptRegistry prompts;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AiEvaluation(AiGateway gateway, AiSettings settings, PromptRegistry prompts, JdbcTemplate jdbc, Clock clock) {
        this.gateway = gateway; this.settings = settings; this.prompts = prompts; this.jdbc = jdbc; this.clock = clock;
    }

    public Path run(Config config) throws IOException, InterruptedException {
        if (!Set.of("dev", "test").contains(config.split()) || config.maxSamples() < 1 || config.delayMs() < 0
                || config.maxConsecutiveErrors() < 1 || config.maxAttempts() < 1 || config.runId() == null || config.gitCommit() == null || config.gitCommit().isBlank())
            throw new IllegalArgumentException("Invalid evaluation configuration");
        var dataset = EvalDataset.read(config.dataset());
        var samples = dataset.samples().stream().filter(s -> s.split().equals(config.split())).limit(config.maxSamples()).toList();
        if (samples.isEmpty()) throw new IllegalArgumentException("No samples in selected split");
        Files.createDirectories(config.outputRoot());
        Path dir = config.outputRoot().resolve(config.runId().toString());
        Files.createDirectories(dir);
        try (var channel = FileChannel.open(dir.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Evaluation run is already active");
            return runLocked(config, dataset, samples, dir);
        }
    }

    private Path runLocked(Config config, EvalDataset.Dataset dataset, List<EvalDataset.Sample> samples, Path dir) throws IOException, InterruptedException {
        Path manifestPath = dir.resolve("run.json"), resultsPath = dir.resolve("per-sample.jsonl"), attemptsPath = dir.resolve("attempts.jsonl");
        Map<String,Object> manifest = new LinkedHashMap<>();
        manifest.put("runId", config.runId().toString()); manifest.put("gitCommit", config.gitCommit());
        manifest.put("gitDirty",config.gitDirty());
        manifest.put("datasetSha256", dataset.sha256()); manifest.put("split", config.split());
        manifest.put("sampleCount", samples.size()); manifest.put("provider", settings.provider());
        manifest.put("model", settings.model(AiUseCase.SIGNAL_EXTRACT)); manifest.put("promptVersion", prompts.version(AiUseCase.SIGNAL_EXTRACT));
        manifest.put("maxSamples", config.maxSamples()); manifest.put("delayMs", config.delayMs());
        manifest.put("maxConsecutiveErrors", config.maxConsecutiveErrors());
        manifest.put("maxAttempts", config.maxAttempts());
        manifest.put("maxContextChars",settings.maxContextChars()); manifest.put("maxTokens",settings.maxTokens());
        manifest.put("timeoutSeconds",settings.timeout()); manifest.put("maxRetries",settings.retries());
        String started;
        if (Files.exists(manifestPath)) {
            JsonNode old = JSON.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
            boolean legacy=old.get("maxAttempts")==null;
            for (var entry : manifest.entrySet()) {
                if (legacy && Set.of("maxAttempts","gitCommit","gitDirty").contains(entry.getKey())) continue;
                if (old.get(entry.getKey()) == null || !entry.getValue().toString().equals(old.get(entry.getKey()).asText()))
                    throw new IllegalArgumentException("Resume configuration does not match run: " + entry.getKey());
            }
            if (legacy) manifest.put("originalGitCommit",old.path("gitCommit").asText());
            else if (old.has("originalGitCommit")) manifest.put("originalGitCommit",old.path("originalGitCommit").asText());
            started = old.path("startedAt").asText();
        } else started = clock.instant().toString();
        manifest.put("startedAt", started);
        if (!Files.exists(manifestPath)) writeJson(manifestPath, manifest);
        Map<String,SampleResult> results = loadResults(resultsPath, samples);
        UUID org = config.runId();
        ensureEvaluationOrganization(org);
        List<Attempt> attempts = loadAttempts(attemptsPath, samples);
        migrateLegacyResults(config, samples, results, attempts);
        writeResults(resultsPath, samples, results);
        writeAttempts(attemptsPath, attempts);
        manifest.put("completedSamples",results.size());manifest.put("complete",results.size()==samples.size());
        writeJson(manifestPath,manifest);
        int consecutive = 0;
        for (var sample : samples) {
            if (results.containsKey(sample.sampleId())) continue;
            if (attempts.stream().noneMatch(a -> a.sampleId().equals(sample.sampleId()))) {
                UUID legacy=UUID.nameUUIDFromBytes((config.runId()+":"+sample.sampleId()).getBytes(StandardCharsets.UTF_8));
                Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM ai_invocations WHERE organization_id=? AND id=? AND subject_id=?",Integer.class,org,legacy,config.runId());
                if (count!=null && count>0) attempts.add(new Attempt(sample.sampleId(),0,legacy,"PENDING",null,null));
            }
            reconcileAttempts(config, sample, attempts);
            writeAttempts(attemptsPath, attempts);
            var prior=attempts.stream().filter(a -> a.sampleId().equals(sample.sampleId())).toList();
            var finalAttempt=prior.stream().filter(a -> a.errorCode()!=null && FINAL_ERRORS.contains(a.errorCode())).reduce((a,b)->b);
            if (finalAttempt.isPresent()) {
                var a=finalAttempt.get(); results.put(sample.sampleId(),empty(sample,a.errorCode(),a.errorDetail(),null));
                writeResults(resultsPath,samples,results); continue;
            }
            int used=prior.size();
            if (used >= config.maxAttempts()) {
                var last=prior.getLast();
                String error=last.errorCode()==null?"CHECKPOINT_LOST":last.errorCode();
                results.put(sample.sampleId(),empty(sample,error,last.errorDetail(),"EXHAUSTED_RETRIES"));
                writeResults(resultsPath,samples,results); continue;
            }
            int attempt=prior.stream().mapToInt(Attempt::attempt).max().orElse(0)+1;
            UUID invocation = UUID.nameUUIDFromBytes((config.runId() + ":" + sample.sampleId()+":"+attempt).getBytes(StandardCharsets.UTF_8));
            int index=attempts.size(); attempts.add(new Attempt(sample.sampleId(),attempt,invocation,"PENDING",null,null));
            writeAttempts(attemptsPath,attempts);
            var result=gateway.invokeEvaluationSignal(invocation,org,config.runId(),sample.text(),SignalExtractionProcessor.SCHEMA);
            var details=jdbc.queryForList("SELECT error_detail FROM ai_invocations WHERE organization_id=? AND id=?",org,invocation);
            String detail=details.isEmpty()?null:(String)details.getFirst().get("error_detail");
            String error=result.errorCode()==null && !"SUCCEEDED".equals(result.status())?result.status():result.errorCode();
            attempts.set(index,new Attempt(sample.sampleId(),attempt,invocation,result.status(),error,detail));
            writeAttempts(attemptsPath,attempts);
            if ("SUCCEEDED".equals(result.status()) || (error!=null && FINAL_ERRORS.contains(error))) {
                results.put(sample.sampleId(),sampleResult(sample,result,detail));
                writeResults(resultsPath,samples,results);
            } else if (used+1 >= config.maxAttempts()) {
                results.put(sample.sampleId(),empty(sample,error,detail,"EXHAUSTED_RETRIES"));
                writeResults(resultsPath,samples,results);
            }
            String stopCode=result.providerErrorCode()!=null?result.providerErrorCode():error;
            consecutive = stopCode!=null && STOP_ERRORS.contains(stopCode) ? consecutive + 1 : 0;
            if (consecutive >= config.maxConsecutiveErrors() || "DISABLED".equals(error) || "BLOCKED_BUDGET".equals(error)) break;
            if (config.delayMs() > 0) Thread.sleep(config.delayMs());
        }
        manifest.put("completedSamples", results.size()); manifest.put("complete", results.size() == samples.size());
        manifest.put("recoveredSuccessWithoutCheckpoint", attempts.stream().filter(a -> "LOST_CHECKPOINT".equals(a.status())).count());
        manifest.put("replayedAfterLostCheckpoint",attempts.stream().filter(a -> "LOST_CHECKPOINT".equals(a.status()) && attempts.stream().anyMatch(b -> b.sampleId().equals(a.sampleId()) && b.attempt()>a.attempt() && !"PENDING".equals(b.status()))).count());
        manifest.put("updatedAt", clock.instant().toString());
        writeJson(manifestPath, manifest);
        writeReports(dir, samples, results, attempts, org, config.runId());
        return dir;
    }

    private void ensureEvaluationOrganization(UUID org) {
        String code = "eval-" + org.toString().replace("-", "");
        var existing = jdbc.queryForList("SELECT code FROM organizations WHERE id=?", String.class, org);
        if (existing.isEmpty()) jdbc.update("INSERT INTO organizations(id,code,name,created_at,updated_at) VALUES (?,?,?, ?, ?)",
                org,code,"AI evaluation",java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));
        else if (!code.equals(existing.getFirst())) throw new IllegalStateException("Evaluation organization ID is already in use");
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE organization_id=?",Integer.class,org);
        if (users == null || users != 0) throw new IllegalStateException("Evaluation organization must have no users");
        for (String table : List.of("leads","customers","conversations","crm_tasks","ai_suggestions","customer_signals")) {
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE organization_id=?",Integer.class,org);
            if (rows == null || rows != 0) throw new IllegalStateException("Evaluation organization contains CRM data");
        }
    }

    private SampleResult sampleResult(EvalDataset.Sample sample, AiGateway.Result result, String detail) throws IOException {
        if (!"SUCCEEDED".equals(result.status())) return empty(sample, result.errorCode() == null ? result.status() : result.errorCode(), detail);
        JsonNode nodes = JSON.readTree(result.signalOutput());
        String intent = EvalMetrics.NO_PREDICTION, sentiment = EvalMetrics.NO_PREDICTION;
        double intentConfidence = -1, sentimentConfidence = -1;
        for (var node : nodes) {
            double confidence = node.path("confidence").asDouble(-1);
            if ("INTENT".equals(node.path("signalType").asText()) && confidence > intentConfidence) {
                intentConfidence = confidence; intent = node.path("value").asText();
            }
            if ("SENTIMENT".equals(node.path("signalType").asText()) && confidence > sentimentConfidence) {
                sentimentConfidence = confidence; sentiment = node.path("value").asText();
            }
        }
        return new SampleResult(sample.sampleId(),sample.goldIntent(),sample.goldSentiment(),intent,sentiment,null,null,null);
    }
    private static SampleResult empty(EvalDataset.Sample sample,String error,String detail) {
        return empty(sample,error,detail,null);
    }
    private static SampleResult empty(EvalDataset.Sample sample,String error,String detail,String disposition) {
        return new SampleResult(sample.sampleId(),sample.goldIntent(),sample.goldSentiment(),EvalMetrics.NO_PREDICTION,EvalMetrics.NO_PREDICTION,error,detail,disposition);
    }
    private void migrateLegacyResults(Config config,List<EvalDataset.Sample> samples,Map<String,SampleResult> results,List<Attempt> attempts) {
        for (var sample : samples) {
            var row=results.get(sample.sampleId());
            if (row==null || row.disposition()!=null || row.errorCode()==null || FINAL_ERRORS.contains(row.errorCode())) continue;
            UUID id=UUID.nameUUIDFromBytes((config.runId()+":"+sample.sampleId()).getBytes(StandardCharsets.UTF_8));
            if (attempts.stream().noneMatch(a -> a.invocationId().equals(id))) {
                String status="CHECKPOINT_LOST".equals(row.errorCode())?"LOST_CHECKPOINT":"FAILED";
                String error="CHECKPOINT_LOST".equals(row.errorCode())?null:row.errorCode();
                attempts.add(new Attempt(sample.sampleId(),0,id,status,error,row.errorDetail()));
            }
            results.remove(sample.sampleId());
        }
    }
    private void reconcileAttempts(Config config,EvalDataset.Sample sample,List<Attempt> attempts) {
        for (int i=0;i<attempts.size();i++) {
            var attempt=attempts.get(i);
            if (!attempt.sampleId().equals(sample.sampleId()) || !("PENDING".equals(attempt.status()) || "SUCCEEDED".equals(attempt.status()))) continue;
            var rows=jdbc.queryForList("SELECT status,error_code,error_detail FROM ai_invocations WHERE organization_id=? AND id=? AND subject_id=?",config.runId(),attempt.invocationId(),config.runId());
            if (rows.isEmpty()) {
                attempts.set(i,new Attempt(attempt.sampleId(),attempt.attempt(),attempt.invocationId(),"FAILED","INTERRUPTED",null));
                continue;
            }
            var stored=rows.getFirst();String status=Objects.toString(stored.get("status"));
            String error=(String)stored.get("error_code"),detail=(String)stored.get("error_detail");
            if ("SUCCEEDED".equals(status)) attempts.set(i,new Attempt(attempt.sampleId(),attempt.attempt(),attempt.invocationId(),"LOST_CHECKPOINT",null,null));
            else attempts.set(i,new Attempt(attempt.sampleId(),attempt.attempt(),attempt.invocationId(),status,error==null?status:error,detail));
        }
    }
    private static List<Attempt> loadAttempts(Path path,List<EvalDataset.Sample> samples) throws IOException {
        var rows=new ArrayList<Attempt>(); if (!Files.exists(path)) return rows;
        var allowed=new HashSet<String>(); samples.forEach(s -> allowed.add(s.sampleId())); var seen=new HashSet<UUID>();
        for (String line:Files.readAllLines(path,StandardCharsets.UTF_8)) {
            var row=JSON.readValue(line,Attempt.class);
            if (!allowed.contains(row.sampleId()) || row.attempt()<0 || row.invocationId()==null || !seen.add(row.invocationId()))
                throw new IllegalArgumentException("Invalid evaluation attempts checkpoint");
            rows.add(row);
        }
        return rows;
    }
    private static void writeAttempts(Path path,List<Attempt> attempts) throws IOException {
        StringBuilder content=new StringBuilder();
        for (var row:attempts) content.append(JSON.writeValueAsString(row)).append('\n');
        atomicWrite(path,content.toString());
    }
    private static Map<String,SampleResult> loadResults(Path path,List<EvalDataset.Sample> samples) throws IOException {
        var results = new LinkedHashMap<String,SampleResult>();
        if (!Files.exists(path)) return results;
        var allowed = new HashSet<String>(); samples.forEach(s -> allowed.add(s.sampleId()));
        for (String line : Files.readAllLines(path,StandardCharsets.UTF_8)) {
            var row = JSON.readValue(line, SampleResult.class);
            if (!allowed.contains(row.sampleId()) || results.putIfAbsent(row.sampleId(),row) != null)
                throw new IllegalArgumentException("Invalid evaluation checkpoint");
        }
        return results;
    }
    private static void writeResults(Path path,List<EvalDataset.Sample> samples,Map<String,SampleResult> results) throws IOException {
        StringBuilder content = new StringBuilder();
        for (var sample : samples) if (results.containsKey(sample.sampleId())) content.append(JSON.writeValueAsString(results.get(sample.sampleId()))).append('\n');
        atomicWrite(path,content.toString());
    }
    private static void writeJson(Path path,Object value) throws IOException { atomicWrite(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)); }
    private static void atomicWrite(Path path,String text) throws IOException {
        Path temp = Files.createTempFile(path.getParent(),"eval-", ".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8);
            try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private void writeReports(Path dir,List<EvalDataset.Sample> samples,Map<String,SampleResult> results,List<Attempt> attempts,UUID org,UUID runId) throws IOException {
        List<EvalMetrics.Pair> intent = new ArrayList<>(), sentiment = new ArrayList<>(), annotationsIntent = new ArrayList<>(), annotationsSentiment = new ArrayList<>();
        Map<String,List<EvalDataset.Sample>> industries = new TreeMap<>(); Map<String,Long> errors = new TreeMap<>(), details = new TreeMap<>();
        List<String> exhausted = new ArrayList<>(); Map<String,Long> infrastructureErrors=new TreeMap<>();
        for (var attempt:attempts) if (attempt.errorCode()!=null && !FINAL_ERRORS.contains(attempt.errorCode())) infrastructureErrors.merge(attempt.errorCode(),1L,Long::sum);
        for (var sample : samples) {
            var result = results.get(sample.sampleId()); if (result == null) continue;
            if ("EXHAUSTED_RETRIES".equals(result.disposition())) { exhausted.add(sample.sampleId()); continue; }
            intent.add(new EvalMetrics.Pair(result.goldIntent(),result.predictedIntent()));
            sentiment.add(new EvalMetrics.Pair(result.goldSentiment(),result.predictedSentiment()));
            industries.computeIfAbsent(sample.industry(),k -> new ArrayList<>()).add(sample);
            if (result.errorCode() != null) errors.merge(result.errorCode(),1L,Long::sum);
            if (result.errorDetail() != null) details.merge(result.errorDetail(),1L,Long::sum);
            if (sample.annotatorA()!=null && sample.annotatorB()!=null) {
                annotationsIntent.add(new EvalMetrics.Pair(sample.annotatorA().intent(),sample.annotatorB().intent()));
                annotationsSentiment.add(new EvalMetrics.Pair(sample.annotatorA().sentiment(),sample.annotatorB().sentiment()));
            }
        }
        Map<String,Object> byIndustry = new TreeMap<>();
        industries.forEach((industry,group) -> {
            if (group.size() < 5) { byIndustry.put(industry,Map.of("samples",group.size(),"status","INSUFFICIENT_SAMPLES")); return; }
            List<EvalMetrics.Pair> a = new ArrayList<>(), b = new ArrayList<>();
            group.forEach(s -> { var r=results.get(s.sampleId());a.add(new EvalMetrics.Pair(r.goldIntent(),r.predictedIntent()));b.add(new EvalMetrics.Pair(r.goldSentiment(),r.predictedSentiment())); });
            byIndustry.put(industry,Map.of("samples",group.size(),"intent",EvalMetrics.calculate(a),"sentiment",EvalMetrics.calculate(b)));
        });
        var rows = jdbc.queryForList("SELECT input_tokens,output_tokens,cost_estimate,latency_ms,status FROM ai_invocations WHERE organization_id=? AND subject_id=? AND use_case='SIGNAL_EXTRACT'",org,runId);
        long input=0,output=0; BigDecimal cost=BigDecimal.ZERO; List<Long> latency=new ArrayList<>();
        for (var row : rows) {
            if (row.get("input_tokens") instanceof Number n) input+=n.longValue();
            if (row.get("output_tokens") instanceof Number n) output+=n.longValue();
            if (row.get("cost_estimate") instanceof BigDecimal n) cost=cost.add(n);
            if ("SUCCEEDED".equals(row.get("status")) && row.get("latency_ms") instanceof Number n) latency.add(n.longValue());
        }
        Collections.sort(latency);
        Map<String,Object> performance=new LinkedHashMap<>();
        performance.put("invocations",rows.size());performance.put("inputTokens",input);performance.put("outputTokens",output);performance.put("totalTokens",input+output);
        long attemptedSamples=attempts.stream().map(Attempt::sampleId).distinct().count();
        performance.put("costEstimateUsd",cost);performance.put("costPer100Usd",attemptedSamples==0?null:cost.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(attemptedSamples),8,java.math.RoundingMode.HALF_UP));
        performance.put("latencyInvocationCount",latency.size());performance.put("latencyP50Ms",percentile(latency,.5));performance.put("latencyP95Ms",percentile(latency,.95));
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("scoredSamples",intent.size());report.put("finalSamples",results.size());report.put("complete",results.size()==samples.size());report.put("intent",intent.isEmpty()?null:EvalMetrics.calculate(intent));report.put("sentiment",sentiment.isEmpty()?null:EvalMetrics.calculate(sentiment));
        report.put("industries",byIndustry);report.put("errors",errors);report.put("errorDetails",details);
        report.put("infrastructure",Map.of("attemptErrors",infrastructureErrors,"exhaustedCount",exhausted.size(),"exhaustedSampleIds",exhausted));
        report.put("annotationPairs",annotationsIntent.size());report.put("intentKappa",EvalMetrics.kappa(annotationsIntent));report.put("sentimentKappa",EvalMetrics.kappa(annotationsSentiment));report.put("performance",performance);
        writeJson(dir.resolve("metrics.json"),report);
        StringBuilder md = new StringBuilder((results.size()==samples.size()?"":"KẾT QUẢ TẠM — chưa đủ mẫu\n\n")+"# Đánh giá tín hiệu AI\n\nSố mẫu chấm chất lượng: "+intent.size()+"/"+samples.size()+"; loại "+exhausted.size()+" mẫu hết lượt thử. Ma trận nhầm lẫn và chi tiết đầy đủ nằm trong `metrics.json`.\n\n"
                +"| Chỉ số | Ý định | Cảm xúc |\n|---|---:|---:|\n| Macro-F1 | "+(intent.isEmpty()?"Không có mẫu":EvalMetrics.calculate(intent).macroF1())+" | "+(sentiment.isEmpty()?"Không có mẫu":EvalMetrics.calculate(sentiment).macroF1())+" |\n| Accuracy | "+(intent.isEmpty()?"Không có mẫu":EvalMetrics.calculate(intent).accuracy())+" | "+(sentiment.isEmpty()?"Không có mẫu":EvalMetrics.calculate(sentiment).accuracy())+" |\n| Coverage | "+(intent.isEmpty()?"Không có mẫu":EvalMetrics.calculate(intent).coverage())+" | "+(sentiment.isEmpty()?"Không có mẫu":EvalMetrics.calculate(sentiment).coverage())+" |\n\n"
                +"Cặp gán nhãn: "+annotationsIntent.size()+"; kappa ý định/cảm xúc: "+EvalMetrics.kappa(annotationsIntent)+"/"+EvalMetrics.kappa(annotationsSentiment)+".\n\n");
        appendClasses(md,"Ý định",EvalMetrics.calculate(intent)); appendClasses(md,"Cảm xúc",EvalMetrics.calculate(sentiment));
        md.append("## Theo ngành\n\nNgành dưới 5 mẫu chỉ ghi cỡ mẫu, chưa báo chỉ số.\n\n| Ngành | Số mẫu |\n|---|---:|\n");
        industries.forEach((industry,group) -> md.append("| ").append(industry).append(" | ").append(group.size()).append(" |\n"));
        md.append("\nMã lỗi mô hình: ").append(errors).append("; chi tiết lỗi: ").append(details).append(".\n\n")
                .append("Lỗi hạ tầng theo lượt thử: ").append(infrastructureErrors).append("; mẫu hết lượt thử (loại khỏi F1): ").append(exhausted).append(".\n\n")
                .append("Token vào/ra: ").append(input).append("/").append(output).append("; chi phí ước tính USD: ").append(cost)
                .append("; chi phí/100 tin USD: ").append(performance.get("costPer100Usd"))
                .append("; p50/p95 (ms) trên ").append(latency.size()).append(" invocation SUCCEEDED: ").append(percentile(latency,.5)).append("/").append(percentile(latency,.95)).append(".\n");
        atomicWrite(dir.resolve("report.md"),md.toString());
    }
    private static void appendClasses(StringBuilder md,String title,EvalMetrics.Result result) {
        md.append("## ").append(title).append(" theo lớp\n\n| Nhãn | Mẫu gold | Precision | Recall | F1 |\n|---|---:|---:|---:|---:|\n");
        result.classes().forEach((label,score) -> md.append("| ").append(label).append(" | ").append(score.support()).append(" | ").append(score.precision()).append(" | ").append(score.recall()).append(" | ").append(score.f1()).append(" |\n"));
        md.append('\n');
    }
    private static Long percentile(List<Long> values,double p) { return values.isEmpty()?null:values.get(Math.max(0,(int)Math.ceil(values.size()*p)-1)); }
}
