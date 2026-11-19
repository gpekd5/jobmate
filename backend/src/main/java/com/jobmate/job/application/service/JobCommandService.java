package com.jobmate.job.application.service;

import com.jobmate.common.exception.CustomException;
import com.jobmate.job.application.command.CollectJobCommand;
import com.jobmate.job.application.command.CreateJobCommand;
import com.jobmate.job.application.command.UpdateJobCommand;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.entity.JobData;
import com.jobmate.job.domain.entity.JobSource;
import com.jobmate.job.domain.exception.JobErrorCode;
import com.jobmate.job.infrastructure.repository.JobRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class JobCommandService {

    private final JobRepository jobRepository;
    private final JobValidator jobValidator;

    public Long create(CreateJobCommand command) {
        jobValidator.validate(command.data());
        return jobRepository.save(Job.create(JobSource.MANUAL, null, command.data(), Instant.now())).getId();
    }

    public Long update(Long jobId, UpdateJobCommand command) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new CustomException(JobErrorCode.JOB_NOT_FOUND));
        if (job.getSource() != JobSource.MANUAL) {
            throw new CustomException(JobErrorCode.JOB_NOT_MANUAL);
        }
        JobData previous = job.data();
        JobData changes = command.data();
        var supplied = command.suppliedFields();
        JobData merged = new JobData(
                supplied.contains("companyName") ? changes.companyName() : previous.companyName(),
                supplied.contains("title") ? changes.title() : previous.title(),
                supplied.contains("jobRole") ? changes.jobRole() : previous.jobRole(),
                supplied.contains("region") ? changes.region() : previous.region(),
                supplied.contains("employmentType") ? changes.employmentType() : previous.employmentType(),
                supplied.contains("experienceText") ? changes.experienceText() : previous.experienceText(),
                supplied.contains("educationText") ? changes.educationText() : previous.educationText(),
                supplied.contains("salaryText") ? changes.salaryText() : previous.salaryText(),
                supplied.contains("originalUrl") ? changes.originalUrl() : previous.originalUrl(),
                supplied.contains("postedAt") ? changes.postedAt() : previous.postedAt(),
                supplied.contains("deadlineAt") ? changes.deadlineAt() : previous.deadlineAt(),
                supplied.contains("active") ? changes.active() : previous.active(),
                supplied.contains("responsibilities") ? changes.responsibilities() : previous.responsibilities(),
                supplied.contains("requiredQualifications") ? changes.requiredQualifications() : previous.requiredQualifications(),
                supplied.contains("preferredQualifications") ? changes.preferredQualifications() : previous.preferredQualifications(),
                supplied.contains("skills") ? changes.skills() : previous.skills(),
                supplied.contains("workConditions") ? changes.workConditions() : previous.workConditions(),
                supplied.contains("detailAvailability") ? changes.detailAvailability() : previous.detailAvailability(),
                supplied.contains("detailSource") ? changes.detailSource() : previous.detailSource(),
                supplied.contains("detailExtractedAt") ? changes.detailExtractedAt() : previous.detailExtractedAt()
        );
        jobValidator.validate(merged);
        job.refresh(merged, Instant.now());
        return job.getId();
    }

    // 허용된 제공처 데이터의 내부 동기화 경계이며 외부 호출이나 수집 API를 만들지 않는다.
    public Long collect(CollectJobCommand command) {
        if (command.source() == null || command.source() == JobSource.MANUAL
                || command.externalJobId() == null || command.externalJobId().isBlank()
                || command.externalJobId().length() > 200 || command.collectedAt() == null) {
            throw new CustomException(JobErrorCode.INVALID_JOB_INPUT);
        }
        jobValidator.validate(command.data());
        var existing = jobRepository.findBySourceAndExternalJobId(command.source(), command.externalJobId());
        if (existing.isPresent()) {
            Job job = existing.get();
            if (command.collectedAt().isBefore(job.getLastCollectedAt())) {
                return job.getId();
            }
            job.refresh(command.data(), command.collectedAt());
            return job.getId();
        }
        return jobRepository.save(Job.create(command.source(), command.externalJobId(),
                command.data(), command.collectedAt())).getId();
    }
}
