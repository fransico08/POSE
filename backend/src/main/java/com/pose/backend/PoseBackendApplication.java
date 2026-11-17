package com.pose.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.MapPropertySource;
import com.pose.backend.ai.eval.EvalDatasourceGuard;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
public class PoseBackendApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(PoseBackendApplication.class);
        application.addListeners((ApplicationEnvironmentPreparedEvent event) -> {
            var env = event.getEnvironment();
            if (env.matchesProfiles("ai-eval")) {
                EvalDatasourceGuard.validateOverrides(env.getProperty("spring.datasource.url"),env.getProperty("spring.flyway.url"),env.getProperty("spring.datasource.hikari.jdbc-url"));
                env.getPropertySources().addFirst(new MapPropertySource("aiEvalIsolation", Map.of(
                        "server.address", "127.0.0.1", "server.port", "0",
                        "pose.ai.signal.auto-extract", "false", "pose.workflow.worker.enabled", "false")));
            }
        });
        var context = application.run(args);
        if (context.getEnvironment().matchesProfiles("ai-eval")) context.close();
    }
}
