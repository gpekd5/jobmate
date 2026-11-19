package com.jobmate.job.domain.exception;

import com.jobmate.common.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum JobErrorCode implements BaseErrorCode {
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다."),
    INVALID_JOB_INPUT(HttpStatus.BAD_REQUEST, "공고 입력값을 확인해 주세요."),
    RESTRICTED_JOB_CONTENT(HttpStatus.BAD_REQUEST, "미제공·제한된 상세 내용은 저장할 수 없습니다."),
    JOB_NOT_MANUAL(HttpStatus.CONFLICT, "개발용 수동 공고만 수정할 수 있습니다."),
    JOB_CONFLICT(HttpStatus.CONFLICT, "공고가 변경되었거나 외부 식별자가 중복되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
