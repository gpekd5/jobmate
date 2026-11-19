package com.jobmate.job.presentation.response;

import com.jobmate.job.application.result.JobResult;
import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.JobSource;
import java.time.Instant;

public record JobSummaryResponse(
        Long jobId,
        JobSource source,
        String companyName,
        String title,
        String jobRole,
        String region,
        String employmentType,
        String experienceText,
        String originalUrl,
        Instant postedAt,
        Instant deadlineAt,
        Boolean active,
        DetailAvailability detailAvailability,
        Instant lastCollectedAt,
        Instant updatedAt,
        long jobVersion
) {

    public static JobSummaryResponse from(JobResult result) {
        var data = result.data();
        return new JobSummaryResponse(
                result.jobId(),
                result.source(),
                data.companyName(),
                data.title(),
                data.jobRole(),
                data.region(),
                data.employmentType(),
                data.experienceText(),
                data.originalUrl(),
                data.postedAt(),
                data.deadlineAt(),
                result.active(),
                data.detailAvailability(),
                result.lastCollectedAt(),
                result.updatedAt(),
                result.jobVersion()
        );
    }
}
