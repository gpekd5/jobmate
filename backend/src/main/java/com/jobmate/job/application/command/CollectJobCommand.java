package com.jobmate.job.application.command;

import com.jobmate.job.domain.entity.JobData;
import com.jobmate.job.domain.entity.JobSource;
import java.time.Instant;

public record CollectJobCommand(JobSource source, String externalJobId, JobData data, Instant collectedAt) {
}
