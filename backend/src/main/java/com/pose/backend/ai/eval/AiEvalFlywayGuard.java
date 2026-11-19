package com.pose.backend.ai.eval;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import java.nio.file.Path;

@Configuration
@Profile("ai-eval")
class AiEvalFlywayGuard {
    @Bean FlywayMigrationStrategy guardedEvaluationMigration(Environment env) {
        return flyway -> {
            String url=env.getProperty("spring.datasource.url");
            EvalDatasourceGuard.validate(url);
            if (url.startsWith("jdbc:postgresql:")) {
                try (var connection=flyway.getConfiguration().getDataSource().getConnection();
                     var statement=connection.createStatement();
                     var result=statement.executeQuery("SHOW data_directory")) {
                    if (!result.next()) throw new IllegalStateException("PostgreSQL data_directory is unavailable");
                    Path actual=Path.of(result.getString(1)).toRealPath();
                    Path expected=Path.of(AiEvaluationRunner.git("rev-parse","--show-toplevel"),"backend","target","auth-postgres-test").toRealPath();
                    if (!actual.equals(expected)) throw new IllegalStateException("PostgreSQL data_directory is not the isolated test cluster");
                } catch (Exception ex) { throw new IllegalStateException("Unable to verify isolated evaluation database",ex); }
            }
            flyway.migrate();
        };
    }
}
