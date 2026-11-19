package com.jobmate.common.ai;

import com.jobmate.common.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements BaseErrorCode {
    AI_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "해당 목적의 OpenAI 전송 동의가 필요합니다."),
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "OpenAI 모델·키·검증한 비용 설정이 필요합니다."),
    AI_BUDGET_EXHAUSTED(HttpStatus.TOO_MANY_REQUESTS, "누적 AI 비용 한도에 도달했습니다."),
    AI_BUSY(HttpStatus.TOO_MANY_REQUESTS, "다른 AI 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    AI_INPUT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "분석 입력이 제한을 초과합니다. 문서를 나누거나 내용을 줄여 주세요."),
    AI_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "OpenAI 요청에 실패했습니다. 잠시 후 명시적으로 다시 시도해 주세요."),
    AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 응답을 검증할 수 없습니다. 분석을 다시 요청해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
