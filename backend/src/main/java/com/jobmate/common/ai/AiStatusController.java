package com.jobmate.common.ai;

import com.jobmate.common.presentation.ApiResponse;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class AiStatusController {

    private final AiSettings settings;
    private final AiBudgetService budget;

    @GetMapping("/api/v1/me/ai-status")
    public ApiResponse<AiStatus> status() {
        BigDecimal reserved = budget.reservedUsd();
        String reason = settings.configurationProblem();
        BigDecimal limit;
        try {
            limit = settings.budgetLimit();
        } catch (IllegalArgumentException exception) {
            limit = BigDecimal.ZERO;
        }
        if (reason == null && reserved.compareTo(limit) >= 0) {
            reason = "AI_BUDGET_EXHAUSTED";
        }
        return ApiResponse.success(new AiStatus(reason == null, reason,
                settings.model(), limit, reserved));
    }

    public record AiStatus(boolean available, String reasonCode, String model,
                           BigDecimal budgetLimitUsd, BigDecimal reservedUsd) {
    }
}
