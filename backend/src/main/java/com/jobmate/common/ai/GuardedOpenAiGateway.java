package com.jobmate.common.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobmate.common.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuardedOpenAiGateway implements AiGateway {

    private final AiSettings settings;
    private final AiConsentChecker consent;
    private final AiBudgetService budget;
    private final PiiMasker masker;
    private final OpenAiTransport transport;
    private final Semaphore inFlight = new Semaphore(2);

    @Override
    public void assertAvailable(AiPurpose purpose) {
        if (!consent.permits(purpose)) {
            throw new CustomException(AiErrorCode.AI_CONSENT_REQUIRED);
        }
        if (settings.configurationProblem() != null) {
            throw new CustomException(AiErrorCode.AI_NOT_CONFIGURED);
        }
        if (budget.reservedUsd().compareTo(settings.budgetLimit()) >= 0) {
            throw new CustomException(AiErrorCode.AI_BUDGET_EXHAUSTED);
        }
    }

    @Override
    public String modelVersion() {
        return settings.model().isBlank() ? "UNCONFIGURED" : settings.model();
    }

    @Override
    public JsonNode complete(AiPurpose purpose, String instructions, String input, JsonNode schema) {
        assertAvailable(purpose);
        long consentVersion = consent.version();
        String masked = masker.mask(input);
        int inputBytes = masked.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > settings.maxInputBytes()) {
            throw new CustomException(AiErrorCode.AI_INPUT_TOO_LARGE);
        }
        // UTF-8 바이트 수 + 구조/메시지 여유를 입력 토큰의 보수적 상한으로 선점한다.
        long tokenBound = (long) inputBytes + instructions.getBytes(StandardCharsets.UTF_8).length
                + schema.toString().getBytes(StandardCharsets.UTF_8).length + 4096;
        BigDecimal maximumCost = settings.inputRate().multiply(BigDecimal.valueOf(tokenBound))
                .add(settings.outputRate().multiply(BigDecimal.valueOf(settings.maxOutputTokens())))
                .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);
        if (!inFlight.tryAcquire()) {
            throw new CustomException(AiErrorCode.AI_BUSY);
        }
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                assertAvailable(purpose);
                requireCurrentConsent(purpose, consentVersion);
                long usageId = budget.reserve(purpose, settings.model(), maximumCost);
                try {
                    var result = transport.request(purpose, instructions, masked, schema);
                    budget.finish(usageId, "SUCCEEDED", result.inputTokens(), result.outputTokens());
                    requireCurrentConsent(purpose, consentVersion);
                    return result.output();
                } catch (OpenAiTransport.TransientFailure exception) {
                    budget.finish(usageId, "FAILED", null, null);
                    if (attempt == 1) {
                        throw new CustomException(AiErrorCode.AI_UNAVAILABLE);
                    }
                } catch (RuntimeException exception) {
                    budget.finish(usageId, "FAILED", null, null);
                    throw exception;
                }
            }
            throw new CustomException(AiErrorCode.AI_UNAVAILABLE);
        } finally {
            inFlight.release();
        }
    }

    private void requireCurrentConsent(AiPurpose purpose, long version) {
        if (!consent.permits(purpose) || consent.version() != version) {
            throw new CustomException(AiErrorCode.AI_CONSENT_REQUIRED);
        }
    }
}
