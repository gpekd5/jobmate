package com.jobmate.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.exception.CustomException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiGuardTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiConsentChecker consent = mock(AiConsentChecker.class);
    private final AiBudgetService budget = mock(AiBudgetService.class);
    private final OpenAiTransport transport = mock(OpenAiTransport.class);
    private final MockEnvironment environment = new MockEnvironment();
    private GuardedOpenAiGateway gateway;

    @BeforeEach
    void 준비() {
        environment.withProperty("jobmate.ai.enabled", "true")
                .withProperty("jobmate.ai.api-key", "test-only-not-a-real-key")
                .withProperty("jobmate.ai.model", "test-model")
                .withProperty("jobmate.ai.input-usd-per-million", "1")
                .withProperty("jobmate.ai.output-usd-per-million", "1");
        when(consent.permits(any())).thenReturn(true);
        when(consent.version()).thenReturn(1L);
        when(budget.reservedUsd()).thenReturn(BigDecimal.ZERO);
        when(budget.reserve(any(), any(), any())).thenReturn(1L);
        gateway = new GuardedOpenAiGateway(new AiSettings(environment), consent, budget, new PiiMasker(), transport);
    }

    @Test
    void 동의가_없으면_유료_호출과_비용_예약을_하지_않는다() {
        // given
        when(consent.permits(any())).thenReturn(false);
        // when, then
        assertThatThrownBy(() -> gateway.complete(AiPurpose.JOB_MATCHING, "rules", "text", mapper.createObjectNode()))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(transport);
        verify(budget, never()).reserve(any(), any(), any());
    }

    @Test
    void 키가_없거나_예산을_소진하면_호출하지_않는다() {
        // given
        environment.setProperty("jobmate.ai.api-key", "");
        // when, then
        assertThatThrownBy(() -> gateway.assertAvailable(AiPurpose.JOB_MATCHING)).isInstanceOf(CustomException.class);
        environment.setProperty("jobmate.ai.api-key", "test-only");
        when(budget.reservedUsd()).thenReturn(new BigDecimal("5"));
        assertThatThrownBy(() -> gateway.complete(AiPurpose.JOB_MATCHING, "rules", "text", mapper.createObjectNode()))
                .isInstanceOf(CustomException.class);
        verifyNoInteractions(transport);
    }

    @Test
    void 일시_실패는_한번만_재시도하고_각_시도에_비용을_예약한다() {
        // given
        when(transport.request(any(), any(), any(), any())).thenThrow(new OpenAiTransport.TransientFailure());
        // when, then
        assertThatThrownBy(() -> gateway.complete(AiPurpose.JOB_MATCHING, "rules", "text", mapper.createObjectNode()))
                .isInstanceOf(CustomException.class);
        verify(transport, times(2)).request(any(), any(), any(), any());
        verify(budget, times(2)).reserve(any(), any(), any());
    }

    @Test
    void 응답_도착전에_동의를_철회하면_결과를_반환하지_않는다() {
        // given
        when(transport.request(any(), any(), any(), any())).thenAnswer(call -> {
            when(consent.version()).thenReturn(2L);
            return new OpenAiTransport.Result(mapper.createObjectNode(), 10, 10);
        });
        // when, then
        assertThatThrownBy(() -> gateway.complete(AiPurpose.PROFILE_EXTRACTION, "rules", "text", mapper.createObjectNode()))
                .isInstanceOf(CustomException.class);
        verify(transport).request(any(), any(), any(), any());
    }

    @Test
    void 개인정보를_마스킹하고_경력_근거는_유지한다() {
        // given
        String input = "이름: 테스트사용자\n메일 test@example.com 전화 010-1234-5678\n900101-1234567\n서울 강남구 테헤란로 123\nJava API 개발 3년";
        // when
        String masked = new PiiMasker().mask(input);
        // then
        assertThat(masked).doesNotContain("테스트사용자", "test@example.com", "010-1234-5678", "900101-1234567", "테헤란로")
                .contains("Java API 개발 3년");
    }

    @Test
    void 지번주소와_괄호전화와_JSON값도_마스킹한다() {
        // given
        String input = "주소 서울 강남구 역삼동 123-4 502호\n전화 02)1234-5678";
        String json = "{\"summary\":\"주소: 서울 강남구 역삼동 123-4 502호\",\"name\":\"Java\"}";
        // when, then
        assertThat(new PiiMasker().mask(input)).doesNotContain("역삼동", "123-4", "1234-5678");
        assertThat(new PiiMasker().mask(json)).doesNotContain("역삼동", "123-4").contains("Java");
    }

    @Test
    void 예산_설정은_오달러를_초과할_수_없다() {
        // given
        environment.setProperty("jobmate.ai.budget-usd", "100");
        // when, then
        assertThat(new AiSettings(environment).budgetLimit()).isEqualByComparingTo("5");
    }
}
