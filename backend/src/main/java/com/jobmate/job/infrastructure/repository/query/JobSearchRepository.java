package com.jobmate.job.infrastructure.repository.query;

import com.jobmate.job.application.command.JobSearchCondition;
import com.jobmate.job.domain.entity.Job;
import java.time.Instant;
import org.springframework.data.domain.Page;

public interface JobSearchRepository {

    Page<Job> search(JobSearchCondition condition, Instant now);
}
