package com.jobmate.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiTransportTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiTransport transport = new OpenAiTransport(new AiSettings(new MockEnvironment()
            .withProperty("jobmate.ai.model", "test-model").withProperty("jobmate.ai.api-key", "test-only")), mapper, builder.build());

    @Test
    void 구조화된_응답을_요청하고_서버_보관을_끈다() throws Exception {
        // given
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"content":[{"type":"output_text","text":"{\\"ok\\":true}"}]}],
                        "usage":{"input_tokens":10,"output_tokens":3}}
                        """, MediaType.APPLICATION_JSON));
        // when
        var result = transport.request(AiPurpose.JOB_MATCHING, "rules", "masked", mapper.createObjectNode());
        // then
        assertThat(result.output().path("ok").asBoolean()).isTrue();
        assertThat(result.inputTokens()).isEqualTo(10);
        server.verify();
    }

    @Test
    void 거절_응답을_분석_성공으로_취급하지_않는다() {
        // given
        server.expect(anything()).andRespond(withSuccess("""
                {"status":"completed","output":[{"content":[{"type":"refusal","refusal":"no"}]}]}
                """, MediaType.APPLICATION_JSON));
        // when, then
        assertThatThrownBy(() -> transport.request(AiPurpose.JOB_MATCHING, "rules", "masked", mapper.createObjectNode()))
                .isInstanceOf(CustomException.class);
        server.verify();
    }
}
