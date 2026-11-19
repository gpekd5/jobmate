package com.jobmate.job.presentation.response;

import com.jobmate.job.application.result.JobResult;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.DetailSource;
import com.jobmate.job.domain.entity.JobSource;
import java.time.Instant;
import java.util.List;

public record JobResponse(
        Long jobId,
        JobSource source,
        String externalJobId,
        String companyName,
        String title,
        String jobRole,
        String region,
        String employmentType,
        String experienceText,
        String educationText,
        String salaryText,
        String originalUrl,
        Instant postedAt,
        Instant deadlineAt,
        Boolean active,
        List<String> responsibilities,
        List<String> requiredQualifications,
        List<String> preferredQualifications,
        List<String> skills,
        List<String> workConditions,
        DetailAvailability detailAvailability,
        DetailSource detailSource,
        Instant detailExtractedAt,
        Instant lastCollectedAt,
        Instant updatedAt,
        long jobVersion
) {

    public static JobResponse from(JobResult result) {
        var data = result.data();
        return new JobResponse(
                result.jobId(),
                result.source(),
                result.externalJobId(),
                data.companyName(),
                data.title(),
                data.jobRole(),
                data.region(),
                data.employmentType(),
                data.experienceText(),
                data.educationText(),
                data.salaryText(),
                data.originalUrl(),
                data.postedAt(),
                data.deadlineAt(),
                result.active(),
                data.responsibilities(),
                data.requiredQualifications(),
                data.preferredQualifications(),
                data.skills(),
                data.workConditions(),
                data.detailAvailability(),
                data.detailSource(),
                data.detailExtractedAt(),
                result.lastCollectedAt(),
                result.updatedAt(),
                result.jobVersion()
        );
    }
}
