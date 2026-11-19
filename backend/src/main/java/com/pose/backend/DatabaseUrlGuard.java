package com.pose.backend;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class DatabaseUrlGuard {
    @Bean
    static BeanFactoryPostProcessor requireDatabaseUrl(Environment environment) {
        return beanFactory -> validate(environment.getProperty("spring.datasource.url"));
    }

    static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Missing required database URL: set POSE_DATABASE_URL (or spring.datasource.url) before starting POSE Backend.");
        }
    }
}
