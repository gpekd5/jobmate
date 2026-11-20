package com.jobmate.common.ai;

import com.jobmate.common.exception.CustomException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiBudgetService {

    private final JdbcTemplate jdbc;
    private final AiSettings settings;
    private final TransactionTemplate transaction;

    public AiBudgetService(JdbcTemplate jdbc, AiSettings settings, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 예약 상한을 영구 차감한다. 타임아웃·재시도에도 발생했을 수 있는 비용을 환급하지 않는다.
    public long reserve(AiPurpose purpose, String model, BigDecimal amount) {
        return transaction.execute(status -> {
            BigDecimal reserved = jdbc.queryForObject(
                    "SELECT reserved_usd FROM ai_budget WHERE budget_id=1 FOR UPDATE", BigDecimal.class);
            BigDecimal charge = amount.setScale(8, RoundingMode.CEILING);
            if (charge.signum() <= 0 || reserved.add(charge).compareTo(settings.budgetLimit()) > 0) {
                throw new CustomException(AiErrorCode.AI_BUDGET_EXHAUSTED);
            }
            jdbc.update("UPDATE ai_budget SET reserved_usd=reserved_usd+? WHERE budget_id=1", charge);
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ai_usage(purpose,model,reserved_usd,state,created_at) VALUES (?,?,?,'RESERVED',UTC_TIMESTAMP(6))",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, purpose.name());
                statement.setString(2, model);
                statement.setBigDecimal(3, charge);
                return statement;
            }, key);
            return key.getKey().longValue();
        });
    }

    public BigDecimal reservedUsd() {
        return jdbc.queryForObject("SELECT reserved_usd FROM ai_budget WHERE budget_id=1", BigDecimal.class);
    }

    public void finish(long usageId, String state, Long inputTokens, Long outputTokens) {
        transaction.executeWithoutResult(status -> jdbc.update(
                "UPDATE ai_usage SET state=?,input_tokens=?,output_tokens=? WHERE usage_id=?",
                state, inputTokens, outputTokens, usageId));
    }
}
