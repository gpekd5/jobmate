package com.jobmate.profile.domain.exception;

import com.jobmate.common.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements BaseErrorCode {
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "분석을 찾을 수 없습니다."),
    PORTFOLIO_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 링크를 찾을 수 없습니다."),
    INVALID_DOCUMENT(HttpStatus.BAD_REQUEST, "지원하지 않거나 안전하게 읽을 수 없는 문서입니다."),
    DOCUMENT_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "문서는 최대 10 MiB까지 업로드할 수 있습니다."),
    DOCUMENT_STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "비공개 문서 저장소를 사용할 수 없습니다."),
    INVALID_PROFILE_INPUT(HttpStatus.BAD_REQUEST, "프로필 입력값을 확인해 주세요."),
    RESUME_REQUIRED(HttpStatus.CONFLICT, "이력서를 먼저 등록해 주세요."),
    ANALYSIS_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 문서 분석이 진행 중입니다."),
    UNSUPPORTED_ANALYSIS_MODE(HttpStatus.BAD_REQUEST, "EXTERNAL_LLM 분석 모드만 지원합니다."),
    PORTFOLIO_READ_FAILED(HttpStatus.BAD_REQUEST, "선택한 공개 GitHub README를 읽을 수 없습니다."),
    PROFILE_CONFLICT(HttpStatus.CONFLICT, "프로필이 변경되었습니다. 다시 확인해 주세요."),
    AI_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "외부 AI 전송 목적과 범위에 동의해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
