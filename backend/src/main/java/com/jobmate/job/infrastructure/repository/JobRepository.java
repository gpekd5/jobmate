package com.jobmate.job.infrastructure.repository;

import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.domain.entity.JobSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.jobmate.job.infrastructure.repository.query.JobSearchRepository;

public interface JobRepository extends JpaRepository<Job, Long>, JobSearchRepository {

    Optional<Job> findBySourceAndExternalJobId(JobSource source, String externalJobId);
}
