package com.pose.backend.ai.eval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
@Profile("ai-eval")
public class AiEvaluationRunner implements ApplicationRunner {
    private final AiEvaluation evaluation;
    private final Environment env;
    private final JdbcTemplate jdbc;
    public AiEvaluationRunner(AiEvaluation evaluation,Environment env,JdbcTemplate jdbc) {
        this.evaluation=evaluation;this.env=env;this.jdbc=jdbc;
    }
    @Override public void run(ApplicationArguments args) throws Exception {
        String url=env.getProperty("spring.datasource.url");
        EvalDatasourceGuard.validate(url);
        Path repo=Path.of(git("rev-parse","--show-toplevel")).toRealPath();
        if (url.startsWith("jdbc:postgresql:")) {
            Path actual=Path.of(Objects.requireNonNull(jdbc.queryForObject("SHOW data_directory",String.class))).toRealPath();
            Path expected=repo.resolve("backend/target/auth-postgres-test").toRealPath();
            if (!actual.equals(expected)) throw new IllegalStateException("PostgreSQL 54329 data_directory is not the isolated test cluster");
        }
        String file=required("pose.ai.eval.file");
        String root=env.getProperty("pose.ai.eval.output-dir",Path.of(System.getProperty("user.home"),"POSE-ai-eval").toString());
        Path output=Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(output);
        if (output.toRealPath().startsWith(repo)) throw new IllegalArgumentException("Evaluation output must be outside the repository");
        String runId=env.getProperty("pose.ai.eval.run-id");
        UUID id=runId==null?UUID.randomUUID():UUID.fromString(runId);
        var config=new AiEvaluation.Config(Path.of(file),env.getProperty("pose.ai.eval.split","test"),
                env.getProperty("pose.ai.eval.max-samples",Integer.class,Integer.MAX_VALUE),env.getProperty("pose.ai.eval.delay-ms",Long.class,1000L),
                env.getProperty("pose.ai.eval.max-consecutive-errors",Integer.class,3),env.getProperty("pose.ai.eval.max-attempts",Integer.class,3),output,id,git("rev-parse","HEAD"),!git("status","--porcelain","--untracked-files=no").isEmpty());
        Path result=evaluation.run(config);
        boolean complete=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(result.resolve("run.json"))).path("complete").asBoolean();
        System.out.println("Evaluation run " + id + (complete ? " completed" : " paused; resume with the same run ID") + "; output: " + result);
    }
    private String required(String property) {
        String value=env.getProperty(property);
        if (value==null || value.isBlank()) throw new IllegalArgumentException("Missing " + property);
        return value;
    }
    static String git(String... args) throws Exception {
        List<String> command=new ArrayList<>();command.add("git");command.addAll(List.of(args));
        Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).strip();
        if (p.waitFor()!=0) throw new IllegalStateException("Unable to determine git checkout or commit");
        return output;
    }
}
