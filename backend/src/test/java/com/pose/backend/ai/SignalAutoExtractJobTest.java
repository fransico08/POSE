package com.pose.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignalAutoExtractJobTest {
    @Test void disabledByDefaultOrWhenAiIsOffDoesNotQueryOrCallProvider() {
        var jdbc=mock(JdbcTemplate.class);var processor=mock(SignalExtractionProcessor.class);
        new SignalAutoExtractJob(jdbc,processor,new AiSettings(new MockEnvironment().withProperty("pose.ai.enabled","true")),Clock.systemUTC()).process();
        new SignalAutoExtractJob(jdbc,processor,new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.auto-extract","true")),Clock.systemUTC()).process();
        verifyNoInteractions(jdbc,processor);
    }
    @Test void batchConfigurationIsBounded() {
        assertThat(new AiSettings(new MockEnvironment()).signalAutoBatch()).isEqualTo(5);
        assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.auto-batch","0")).signalAutoBatch()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.auto-batch","21")).signalAutoBatch()).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void autoConfigurationIsValidatedDuringJobConstruction() {
        for(var invalid:java.util.Map.of("pose.ai.signal.auto-batch","0","pose.ai.signal.auto-lookback-hours","0",
                "pose.ai.signal.auto-max-attempts","11","pose.ai.signal.auto-retry-delay-minutes","1441").entrySet()) {
            var environment=new MockEnvironment().withProperty("pose.ai.signal.auto-extract","true").withProperty(invalid.getKey(),invalid.getValue());
            assertThatThrownBy(()->new SignalAutoExtractJob(mock(JdbcTemplate.class),mock(SignalExtractionProcessor.class),new AiSettings(environment),Clock.systemUTC()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(invalid.getKey());
        }
        var defaults=new AiSettings(new MockEnvironment());
        assertThat(defaults.signalAutoMaxAttempts()).isEqualTo(3);
        assertThat(defaults.signalAutoRetryDelayMinutes()).isEqualTo(15);
        assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.auto-max-attempts","0")).signalAutoMaxAttempts()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AiSettings(new MockEnvironment().withProperty("pose.ai.signal.auto-retry-delay-minutes","0")).signalAutoRetryDelayMinutes()).isInstanceOf(IllegalArgumentException.class);
    }
}
