package com.jobmate.job.domain.entity;

import com.jobmate.job.domain.policy.JobContentPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job", uniqueConstraints = @UniqueConstraint(name = "uk_job_source_external_id", columnNames = {"source", "external_job_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobSource source;

    @Column(length = 200)
    private String externalJobId;

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 100)
    private String jobRole;

    @Column(length = 100)
    private String region;

    @Column(length = 100)
    private String employmentType;

    @Column(length = 300)
    private String experienceText;

    @Column(length = 300)
    private String educationText;

    @Column(length = 300)
    private String salaryText;

    @Column(length = 2000)
    private String originalUrl;

    private Instant postedAt;

    private Instant deadlineAt;

    @Column(nullable = false)
    private Boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> responsibilities;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> requiredQualifications;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> preferredQualifications;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> skills;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> workConditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DetailAvailability detailAvailability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DetailSource detailSource;

    private Instant detailExtractedAt;

    @Column(nullable = false)
    private Instant lastCollectedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private long jobVersion;

    @Version
    @Column(nullable = false)
    private long lockVersion;

    public static Job create(JobSource source, String externalJobId, JobData data, Instant collectedAt) {
        Job job = new Job();
        job.source = source;
        job.externalJobId = externalJobId;
        job.replace(data);
        job.lastCollectedAt = collectedAt.truncatedTo(ChronoUnit.MICROS);
        job.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        job.jobVersion = 1;
        return job;
    }

    // 수집 시각만 바뀐 재수집은 내용 버전과 수정 시각을 변경하지 않는다.
    public void refresh(JobData data, Instant collectedAt) {
        boolean contentChanged = !data().hasSameContentAs(data);
        replace(data);
        if (contentChanged) {
            jobVersion++;
            updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
        if (collectedAt.isAfter(lastCollectedAt)) {
            lastCollectedAt = collectedAt.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public boolean isActiveAt(Instant now) {
        return Boolean.TRUE.equals(active) && (deadlineAt == null || deadlineAt.isAfter(now));
    }

    public JobData data() {
        return new JobData(
                companyName,
                title,
                jobRole,
                region,
                employmentType,
                experienceText,
                educationText,
                salaryText,
                originalUrl,
                postedAt,
                deadlineAt,
                active,
                responsibilities,
                requiredQualifications,
                preferredQualifications,
                skills,
                workConditions,
                detailAvailability,
                detailSource,
                detailExtractedAt
        );
    }

    private void replace(JobData data) {
        JobContentPolicy.validate(data);
        this.companyName = data.companyName();
        this.title = data.title();
        this.jobRole = data.jobRole();
        this.region = data.region();
        this.employmentType = data.employmentType();
        this.experienceText = data.experienceText();
        this.educationText = data.educationText();
        this.salaryText = data.salaryText();
        this.originalUrl = data.originalUrl();
        this.postedAt = data.postedAt();
        this.deadlineAt = data.deadlineAt();
        this.active = data.active();
        this.responsibilities = data.responsibilities() == null ? null : List.copyOf(data.responsibilities());
        this.requiredQualifications = data.requiredQualifications() == null ? null : List.copyOf(data.requiredQualifications());
        this.preferredQualifications = data.preferredQualifications() == null ? null : List.copyOf(data.preferredQualifications());
        this.skills = data.skills() == null ? null : List.copyOf(data.skills());
        this.workConditions = data.workConditions() == null ? null : List.copyOf(data.workConditions());
        this.detailAvailability = data.detailAvailability();
        this.detailSource = data.detailSource();
        this.detailExtractedAt = data.detailExtractedAt();
    }
}
