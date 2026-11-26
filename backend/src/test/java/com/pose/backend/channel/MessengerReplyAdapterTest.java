package com.pose.backend.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pose.backend.api.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessengerReplyAdapterTest {
    @Test
    void sendsThroughConfiguredPageAndReturnsGraphMessageId() throws Exception {
        var environment = new MockEnvironment()
                .withProperty("POSE_CHANNEL_MESSENGER_PAGE_TOKEN", "synthetic-page-token")
                .withProperty("POSE_CHANNEL_MESSENGER_GRAPH_API_VERSION", "v26.0");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"recipient_id\":\"test-psid\",\"message_id\":\"test-mid\"}");
        doReturn(response).when(client).send(any(HttpRequest.class), stringBodyHandlerMatcher());
        var adapter = new MessengerReplyAdapter(environment, new ObjectMapper(), client);

        String id = adapter.send("test-page", "test-psid", "Xin chào");

        assertThat(id).isEqualTo("test-mid");
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), stringBodyHandlerMatcher());
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo("https://graph.facebook.com/v26.0/test-page/messages");
        assertThat(request.headers().firstValue("Content-Type")).contains("application/json");
        assertThat(request.headers().firstValue("Authorization")).contains("Bearer synthetic-page-token");
    }

    @Test
    void providerFailureBecomesSanitizedGatewayError() throws Exception {
        var environment = new MockEnvironment()
                .withProperty("POSE_CHANNEL_MESSENGER_PAGE_TOKEN", "synthetic-page-token");
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("provider response must not be returned to CRM");
        doReturn(response).when(client).send(any(HttpRequest.class), stringBodyHandlerMatcher());
        var adapter = new MessengerReplyAdapter(environment, new ObjectMapper(), client);

        assertThatThrownBy(() -> adapter.send("test-page", "test-psid", "Xin chào"))
                .isInstanceOf(ApiRequestException.class)
                .satisfies(error -> {
                    var apiError = (ApiRequestException) error;
                    assertThat(apiError.status()).isEqualTo(502);
                    assertThat(apiError.code()).isEqualTo("CHANNEL_REPLY_FAILED");
                    assertThat(apiError.getMessage()).doesNotContain("provider response", "synthetic-page-token");
                });
    }

    @Test
    void missingTokenFailsWithoutCallingGraph() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var adapter = new MessengerReplyAdapter(new MockEnvironment(), new ObjectMapper(), client);

        assertThatThrownBy(() -> adapter.send("test-page", "test-psid", "Xin chào"))
                .isInstanceOf(ApiRequestException.class)
                .satisfies(error -> assertThat(((ApiRequestException) error).code())
                        .isEqualTo("CHANNEL_REPLY_NOT_CONFIGURED"));
        verify(client, never()).send(any(HttpRequest.class), stringBodyHandlerMatcher());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static HttpResponse.BodyHandler<String> stringBodyHandlerMatcher() {
        return (HttpResponse.BodyHandler) any(HttpResponse.BodyHandler.class);
    }
}
