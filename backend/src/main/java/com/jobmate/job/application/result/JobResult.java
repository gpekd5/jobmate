package com.jobmate.job.application.result;

import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.job.domain.entity.JobSource;
import java.time.Instant;

public record JobResult(
        Long jobId,
        JobSource source,
        String externalJobId,
        JobData data,
        boolean active,
        Instant lastCollectedAt,
        Instant updatedAt,
        long jobVersion
) {

    public static JobResult from(Job job, Instant now) {
        return new JobResult(job.getId(), job.getSource(), job.getExternalJobId(), job.data(),
                job.isActiveAt(now), job.getLastCollectedAt(), job.getUpdatedAt(), job.getJobVersion());
    }
}
