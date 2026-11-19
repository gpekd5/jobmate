package com.jobmate.job.application.command;

import com.jobmate.job.domain.entity.JobData;

public record CreateJobCommand(JobData data) {
}
