package com.jobmate.job.application.service;

import com.jobmate.job.domain.entity.JobData;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobValidator {

    private final Validator validator;

    // PATCH 병합 후에도 등록과 동일한 입력 형식과 길이 제약을 검증한다.
    public void validate(JobData data) {
        var violations = validator.validate(data);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
