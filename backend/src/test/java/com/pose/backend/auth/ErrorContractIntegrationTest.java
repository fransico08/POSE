package com.pose.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ErrorContractIntegrationTest extends FoundationTestSupport {
    private JsonNode check(ResultActions result,int status,String code) throws Exception {
        var response=result.andExpect(status().is(status)).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().exists("X-Trace-Id")).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").isString()).andReturn().getResponse();
        JsonNode body=mapper.readTree(response.getContentAsString());
        assertThat(body.get("traceId").asText()).isEqualTo(response.getHeader("X-Trace-Id"));
        return body;
    }
    @Test void errorsShareEnvelopeAcrossMvcAndSecurity() throws Exception {
        JsonNode actor=loginOne();String token=bearer(actor);
        JsonNode missing=check(mvc.perform(get("/api/v1/test/error").header("Authorization",token)),400,"VALIDATION_ERROR");
        assertThat(missing.at("/fieldErrors/0/field").asText()).isEqualTo("required");
        check(mvc.perform(post("/api/v1/test/error").header("Authorization",token)),405,"METHOD_NOT_ALLOWED");
        check(mvc.perform(get("/api/v1/nonexistent-w10").header("Authorization",token)),404,"RESOURCE_NOT_FOUND");
        check(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.TEXT_PLAIN).content("not-json")),415,"UNSUPPORTED_MEDIA_TYPE");
        check(mvc.perform(get("/api/v1/users")),401,"AUTH_INVALID_TOKEN");
        JsonNode other=response(login("two","shared@example.com",PASSWORD_TWO),200);
        check(mvc.perform(get("/api/v1/test/feedback").header("Authorization",bearer(other))),403,"FORBIDDEN_PERMISSION");
        JsonNode failure=check(mvc.perform(get("/api/v1/test/error?required=ok&fail=true").header("Authorization",token)),500,"INTERNAL_ERROR");
        assertThat(failure.toString()).doesNotContain("private diagnostic","IllegalStateException","stack","SQL");
    }
}
