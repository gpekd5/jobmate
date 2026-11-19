package com.jobmate.job.presentation.response;

import com.jobmate.job.application.result.JobResult;
import java.util.List;
import org.springframework.data.domain.Page;

public record JobPageResponse(
        List<JobSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static JobPageResponse from(Page<JobResult> page) {
        return new JobPageResponse(page.getContent().stream().map(JobSummaryResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
