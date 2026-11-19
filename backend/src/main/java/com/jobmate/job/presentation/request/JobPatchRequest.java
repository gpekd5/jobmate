package com.jobmate.job.presentation.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.exception.CustomException;
import com.jobmate.job.application.command.UpdateJobCommand;
import com.jobmate.job.domain.exception.JobErrorCode;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;

public record JobPatchRequest(@NotNull JsonNode fields) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public JobPatchRequest {
    }

    public UpdateJobCommand toCommand(ObjectMapper objectMapper) {
        Set<String> allowed = Set.of("companyName", "title", "jobRole", "region", "employmentType", "experienceText", "educationText", "salaryText", "originalUrl", "postedAt", "deadlineAt", "active", "responsibilities", "requiredQualifications", "preferredQualifications", "skills", "workConditions", "detailAvailability", "detailSource", "detailExtractedAt");
        if (!fields.isObject() || fields.isEmpty()) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
        var supplied = new HashSet<String>();
        fields.fieldNames().forEachRemaining(supplied::add);
        if (!allowed.containsAll(supplied)) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
        try {
            JobCreateRequest values = objectMapper.treeToValue(fields, JobCreateRequest.class);
            return new UpdateJobCommand(values.toData(false), Set.copyOf(supplied));
        } catch (JsonProcessingException exception) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
    }
}
