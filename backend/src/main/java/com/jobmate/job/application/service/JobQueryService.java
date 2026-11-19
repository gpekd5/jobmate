package com.jobmate.job.application.service;

import com.jobmate.common.exception.CustomException;
import com.jobmate.job.application.command.JobSearchCondition;
import com.jobmate.job.application.result.JobResult;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.exception.JobErrorCode;
import com.jobmate.job.infrastructure.repository.JobRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobQueryService {

    private final JobRepository jobRepository;

    public JobResult get(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(JobErrorCode.JOB_NOT_FOUND));
        return JobResult.from(job, Instant.now());
    }

    public Page<JobResult> search(JobSearchCondition condition) {
        if ((long) condition.page() * condition.size() > Integer.MAX_VALUE) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
        Instant now = Instant.now();
        return jobRepository.search(condition, now).map(job -> JobResult.from(job, now));
    }
}
