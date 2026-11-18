package com.pose.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class MockProviderSignalTest {
    private final MockProvider provider=new MockProvider();
    private final ObjectMapper json=new ObjectMapper();

    private List<SignalOutputFilter.Signal> signals(String message) throws Exception {
        String context=json.writeValueAsString(Map.of("message",message));
        var reply=provider.generate(new AiProvider.Request(AiUseCase.SIGNAL_EXTRACT,"prompt",AiContextBuilder.boundUntrustedContext(context),Map.of("type","object")));
        assertThat(reply.errorCode()).isNull();
        return new SignalOutputFilter().filter(reply.text(),message).orElseThrow();
    }
    @Test void recognizesPriceWithAndWithoutAccentsAndPunctuation() throws Exception {
        for(String text:List.of("HOC PHI bao nhieu?","Giá khóa này?","Bao nhiêu tiền?","học phí thế nào?"))
            assertThat(signals(text)).extracting(SignalOutputFilter.Signal::value).containsExactly("ASK_PRICE");
    }
    @Test void recognizesBooking() throws Exception {
        for(String text:List.of("Tôi muốn đặt lịch!","DANG KY ngay"))
            assertThat(signals(text)).extracting(SignalOutputFilter.Signal::value).containsExactly("BOOKING");
    }
    @Test void recognizesComplaintsWithNegativeSentiment() throws Exception {
        for(String text:List.of("Toi muon khieu nai","Chưa hài lòng.","Rất TỆ!"))
            assertThat(signals(text)).extracting(SignalOutputFilter.Signal::value).containsExactly("COMPLAINT","NEGATIVE");
    }
    @Test void recognizesPositiveSentimentWithoutInventingIntent() throws Exception {
        for(String text:List.of("CAM ON nhé!","Dịch vụ TỐT."))
            assertThat(signals(text)).extracting(SignalOutputFilter.Signal::value).containsExactly("OTHER","POSITIVE");
    }
    @Test void unrelatedWordsDoNotMatchShortKeywords() throws Exception {
        for(String text:List.of("gia đình","tham gia","test","tot nghiep","Xin chào #$%"))
            assertThat(signals(text)).extracting(SignalOutputFilter.Signal::value).containsExactly("OTHER");
    }
    @Test void handoverDoesNotInferMissingWork() {
        String context=AiContextBuilder.boundUntrustedContext("{\"source\":\"PROFILE\",\"name\":\"Khách mẫu\",\"status\":\"ACTIVE\"}");
        var reply=provider.generate(new AiProvider.Request(AiUseCase.HANDOVER_BRIEF,"prompt",context,Map.of("type","object")));
        assertThat(reply.text()).contains("Chưa có dữ liệu").doesNotContain("chưa có cơ hội", "liên hệ làm quen");
    }
}
