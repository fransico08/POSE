package com.pose.backend.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class AiEvaluationCommandTest {
    @TempDir Path output;
    @Test void profileRunsAsAnApplicationAndStopsAfterEvaluation() throws Exception {
        Path input=Path.of(Objects.requireNonNull(getClass().getResource("/ai-eval/synthetic.jsonl")).toURI());
        UUID run=UUID.randomUUID();
        var result=command("--spring.profiles.active=ai-eval",
                "--spring.datasource.url=jdbc:h2:mem:evalcommand;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "--spring.datasource.username=sa","--spring.datasource.password=",
                "--spring.flyway.locations=classpath:db/auth-test-baseline,classpath:db/migration",
                "--spring.jpa.hibernate.ddl-auto=none", "--pose.security.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "--pose.ai.enabled=true","--pose.ai.provider=mock","--logging.level.root=ERROR",
                "--pose.ai.eval.file="+input,"--pose.ai.eval.output-dir="+output,
                "--pose.ai.eval.run-id="+run,"--pose.ai.eval.delay-ms=0");
        assertThat(result.exitCode()).as(result.output()).isZero();
        var runInfo=new ObjectMapper().readTree(Files.readString(output.resolve(run.toString()).resolve("run.json")));
        assertThat(runInfo.path("sampleCount").asInt()).isEqualTo(3);
        assertThat(runInfo.path("complete").asBoolean()).isTrue();
    }
    @Test void profileRejectsPort5432BeforeStartingContext() throws Exception {
        var result=command("--spring.profiles.active=ai-eval", "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/pose");
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("AI evaluation requires an isolated");
    }
    private record CommandResult(int exitCode,String output) {}
    private CommandResult command(String... args) throws Exception {
        var command=new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("-cp");command.add(System.getProperty("java.class.path"));
        command.add("com.pose.backend.PoseBackendApplication");command.addAll(List.of(args));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        return new CommandResult(process.waitFor(),output);
    }
}
