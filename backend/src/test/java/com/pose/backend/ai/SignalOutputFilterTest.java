package com.pose.backend.ai;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SignalOutputFilterTest {
    private final SignalOutputFilter filter=new SignalOutputFilter();
    @Test void keepsOnlyAllowedLabelsConfidenceAndVerbatimEvidence() {
        String output="""
                {"signals":[
                  {"signalType":"INTENT","value":"ASK_PRICE","confidence":0.9,"evidenceText":"hỏi giá"},
                  {"signalType":"SENTIMENT","value":"NEGATIVE","confidence":1.1,"evidenceText":"hỏi giá"},
                  {"signalType":"SENTIMENT","value":"POSITIVE","confidence":0.4,"evidenceText":"không có"},
                  {"signalType":"INTENT","value":"UNKNOWN","confidence":0.4,"evidenceText":"hỏi giá"}]}
                """;
        var result=filter.filter(output,"Xin hỏi giá [PHONE_1]");
        assertThat(result).isPresent();assertThat(result.orElseThrow()).hasSize(1);assertThat(result.orElseThrow().getFirst().value()).isEqualTo("ASK_PRICE");
        assertThat(filter.filter("{}","tin nhắn")).isEmpty();
    }
    @Test void emptySignalsAreValidButAllRejectedAreInvalid() {
        assertThat(filter.filter("{\"signals\":[]}","hello").orElseThrow()).isEmpty();
        assertThat(filter.filter("{\"signals\":[{\"signalType\":\"BAD\"}]}","hello")).isEmpty();
    }
    @Test void doesNotAcceptUnredactedEvidence() {
        String output="{\"signals\":[{\"signalType\":\"INTENT\",\"value\":\"OTHER\",\"confidence\":0.5,\"evidenceText\":\"0912345678\"}]}";
        assertThat(filter.filter(output,"Số [PHONE_1]")).isEmpty();
    }
}
