package com.jobmate.common.ai;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiSettings {

    private final Environment environment;

    public String apiKey() {
        return value("api-key", "");
    }

    public String model() {
        return value("model", "");
    }

    public int maxOutputTokens() {
        return Integer.parseInt(value("max-output-tokens", "2048"));
    }

    public int timeoutSeconds() {
        return Integer.parseInt(value("timeout-seconds", "30"));
    }

    public int maxInputBytes() {
        return Integer.parseInt(value("max-input-bytes", "24000"));
    }

    public BigDecimal inputRate() {
        return new BigDecimal(value("input-usd-per-million", "0"));
    }

    public BigDecimal outputRate() {
        return new BigDecimal(value("output-usd-per-million", "0"));
    }

    public BigDecimal budgetLimit() {
        return new BigDecimal(value("budget-usd", "5")).min(new BigDecimal("5"));
    }

    public String configurationProblem() {
        try {
            if (!Boolean.parseBoolean(value("enabled", "false")) || apiKey().isBlank()
                    || !model().matches("[A-Za-z0-9._:-]{1,100}")
                    || inputRate().signum() <= 0 || outputRate().signum() <= 0
                    || budgetLimit().signum() <= 0 || maxOutputTokens() < 16 || maxOutputTokens() > 4096
                    || timeoutSeconds() < 1 || timeoutSeconds() > 60
                    || maxInputBytes() < 1024 || maxInputBytes() > 24000) {
                return "AI_NOT_CONFIGURED";
            }
        } catch (IllegalArgumentException exception) {
            return "AI_NOT_CONFIGURED";
        }
        return null;
    }

    private String value(String name, String fallback) {
        return environment.getProperty("jobmate.ai." + name, fallback).strip();
    }
}
