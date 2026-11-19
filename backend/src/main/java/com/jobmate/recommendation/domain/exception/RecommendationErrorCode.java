package com.jobmate.recommendation.domain.exception;

import com.jobmate.common.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RecommendationErrorCode implements BaseErrorCode {
    PROFILE_NOT_CONFIRMED(HttpStatus.CONFLICT, "먼저 프로필을 확인하고 확정해 주세요."),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."),
    INVALID_RECOMMENDATION_QUERY(HttpStatus.BAD_REQUEST, "추천 검색 조건을 확인해 주세요. 읽음 필터는 아직 지원하지 않습니다."),
    MATCH_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "하루 개별 AI 분석은 3건까지 요청할 수 있습니다."),
    AI_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "현재 목적에 대한 외부 AI 전송 동의가 필요합니다."),
    INVALID_AI_MATCH(HttpStatus.BAD_GATEWAY, "AI 분석 결과가 검증 기준을 충족하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
