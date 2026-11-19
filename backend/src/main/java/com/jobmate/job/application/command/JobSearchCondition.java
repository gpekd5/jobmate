package com.jobmate.job.application.command;

import com.jobmate.job.domain.entity.JobSort;
import com.jobmate.job.domain.entity.JobSource;

public record JobSearchCondition(
        String keyword,
        String region,
        String jobRole,
        JobSource source,
        boolean activeOnly,
        int page,
        int size,
        JobSort sort
) {
}
