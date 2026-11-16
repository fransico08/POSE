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
}
