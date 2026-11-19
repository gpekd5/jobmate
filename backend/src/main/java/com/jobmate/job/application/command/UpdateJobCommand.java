package com.jobmate.job.application.command;

import com.jobmate.job.domain.entity.JobData;
import java.util.Set;

public record UpdateJobCommand(JobData data, Set<String> suppliedFields) {
}
