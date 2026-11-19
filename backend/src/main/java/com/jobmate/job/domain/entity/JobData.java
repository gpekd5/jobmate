package com.jobmate.job.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public record JobData(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 100) String jobRole,
        @Size(max = 100) String region,
        @Size(max = 100) String employmentType,
        @Size(max = 300) String experienceText,
        @Size(max = 300) String educationText,
        @Size(max = 300) String salaryText,
        @Size(max = 2000) @URL(regexp = "https?://.+") String originalUrl,
        Instant postedAt,
        Instant deadlineAt,
        @NotNull Boolean active,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> responsibilities,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> requiredQualifications,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> preferredQualifications,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> skills,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> workConditions,
        @NotNull DetailAvailability detailAvailability,
        @NotNull DetailSource detailSource,
        Instant detailExtractedAt
) {

    public JobData {
        // MySQL DATETIME(6)의 정밀도와 맞춰 동일한 수집 내용을 안정적으로 비교한다.
        postedAt = postedAt == null ? null : postedAt.truncatedTo(ChronoUnit.MICROS);
        deadlineAt = deadlineAt == null ? null : deadlineAt.truncatedTo(ChronoUnit.MICROS);
        detailExtractedAt = detailExtractedAt == null ? null : detailExtractedAt.truncatedTo(ChronoUnit.MICROS);
    }

    // 확보 시각은 provenance이며 추천 캐시를 무효화하는 공고 내용에 포함하지 않는다.
    public boolean hasSameContentAs(JobData other) {
        return Objects.equals(companyName, other.companyName())
                && Objects.equals(title, other.title())
                && Objects.equals(jobRole, other.jobRole())
                && Objects.equals(region, other.region())
                && Objects.equals(employmentType, other.employmentType())
                && Objects.equals(experienceText, other.experienceText())
                && Objects.equals(educationText, other.educationText())
                && Objects.equals(salaryText, other.salaryText())
                && Objects.equals(originalUrl, other.originalUrl())
                && Objects.equals(postedAt, other.postedAt())
                && Objects.equals(deadlineAt, other.deadlineAt())
                && Objects.equals(active, other.active())
                && Objects.equals(responsibilities, other.responsibilities())
                && Objects.equals(requiredQualifications, other.requiredQualifications())
                && Objects.equals(preferredQualifications, other.preferredQualifications())
                && Objects.equals(skills, other.skills())
                && Objects.equals(workConditions, other.workConditions())
                && Objects.equals(detailAvailability, other.detailAvailability())
                && Objects.equals(detailSource, other.detailSource());
    }

}
