package com.jobmate.job.presentation.request;

import com.jobmate.job.domain.entity.DetailAvailability;
import com.jobmate.job.domain.entity.DetailSource;
import com.jobmate.job.domain.entity.JobData;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobCreateRequest(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 300) String title,
        String jobRole,
        String region,
        String employmentType,
        String experienceText,
        String educationText,
        String salaryText,
        String originalUrl,
        Instant postedAt,
        Instant deadlineAt,
        Boolean active,
        List<String> responsibilities,
        List<String> requiredQualifications,
        List<String> preferredQualifications,
        List<String> skills,
        List<String> workConditions,
        DetailAvailability detailAvailability,
        DetailSource detailSource,
        Instant detailExtractedAt
) {

    public JobData toData(boolean defaults) {
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
                defaults && active == null ? Boolean.TRUE : active,
                responsibilities,
                requiredQualifications,
                preferredQualifications,
                skills,
                workConditions,
                defaults && detailAvailability == null ? inferredAvailability() : detailAvailability,
                defaults && detailSource == null ? DetailSource.USER_PROVIDED : detailSource,
                detailExtractedAt
        );
    }

    private DetailAvailability inferredAvailability() {
        boolean any = responsibilities != null || requiredQualifications != null
                || preferredQualifications != null || skills != null || workConditions != null;
        boolean all = responsibilities != null && requiredQualifications != null
                && preferredQualifications != null && skills != null && workConditions != null;
        return all ? DetailAvailability.AVAILABLE : any ? DetailAvailability.PARTIAL : DetailAvailability.UNAVAILABLE;
    }
}
