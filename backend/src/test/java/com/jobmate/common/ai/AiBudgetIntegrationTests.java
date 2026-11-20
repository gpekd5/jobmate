package com.jobmate.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobmate.common.exception.CustomException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "jobmate.recommendation.scheduling-enabled=false")
class AiBudgetIntegrationTests {
    @Autowired private AiBudgetService budget;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void 동시_비용_예약에도_누적_오달러_상한을_넘지_않는다() throws Exception {
        // given: 테스트 전용 DB에서만 비용 원장을 초기화한다.
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbc.update("DELETE FROM ai_usage");
        jdbc.update("UPDATE ai_budget SET reserved_usd=0 WHERE budget_id=1");
        var executor = Executors.newFixedThreadPool(10);
        try {
            var calls = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 10; i++) {
                calls.add(() -> {
                    try {
                        budget.reserve(AiPurpose.JOB_MATCHING, "test-model", BigDecimal.ONE);
                        return true;
                    } catch (CustomException exception) {
                        return false;
                    }
                });
            }
            // when
            long successes = 0;
            for (var result : executor.invokeAll(calls, 20, TimeUnit.SECONDS)) {
                if (result.get()) successes++;
            }
            // then
            assertThat(successes).isEqualTo(5);
            assertThat(budget.reservedUsd()).isEqualByComparingTo("5");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_usage", Integer.class)).isEqualTo(5);
        } finally {
            executor.shutdownNow();
            jdbc.update("DELETE FROM ai_usage");
            jdbc.update("UPDATE ai_budget SET reserved_usd=0 WHERE budget_id=1");
        }
    }
}
