package com.pose.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlGuardTest {
    @Test
    void validateRejectsMissingOrBlankUrlWithoutLeakingCredentials() {
        assertThatThrownBy(() -> DatabaseUrlGuard.validate(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSE_DATABASE_URL")
                .hasMessageNotContaining("password");
        assertThatThrownBy(() -> DatabaseUrlGuard.validate("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POSE_DATABASE_URL");
        assertThatCode(() -> DatabaseUrlGuard.validate("jdbc:h2:mem:test")).doesNotThrowAnyException();
        assertThatCode(() -> DatabaseUrlGuard.validate("jdbc:postgresql://127.0.0.1:54329/pose_test")).doesNotThrowAnyException();
    }

    @Test
    void applicationFailsFastWhenDatabaseUrlIsMissing() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(PoseBackendApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=",
                        "spring.datasource.password=top-secret-should-never-appear"
                );
        assertThatThrownBy(builder::run)
                .hasStackTraceContaining("POSE_DATABASE_URL")
                .satisfies(ex -> assertThat(ex.toString()).doesNotContain("top-secret-should-never-appear"));
    }
}
