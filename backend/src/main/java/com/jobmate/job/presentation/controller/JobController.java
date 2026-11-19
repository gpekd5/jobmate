package com.jobmate.job.presentation.controller;

import com.jobmate.common.presentation.ApiResponse;
import com.jobmate.job.application.command.JobSearchCondition;
import com.jobmate.job.application.service.JobQueryService;
import com.jobmate.job.domain.entity.JobSort;
import com.jobmate.job.domain.entity.JobSource;
import com.jobmate.job.presentation.response.JobPageResponse;
import com.jobmate.job.presentation.response.JobResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Validated
public class JobController {

    private final JobQueryService jobQueryService;

    @GetMapping
    public ApiResponse<JobPageResponse> list(
            @RequestParam(required = false) @Size(max = 300) String keyword,
            @RequestParam(required = false) @Size(max = 100) String region,
            @RequestParam(required = false) @Size(max = 100) String jobRole,
            @RequestParam(required = false) JobSource source,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "NEWEST") JobSort sort
    ) {
        var condition = new JobSearchCondition(keyword, region, jobRole, source, activeOnly, page, size, sort);
        return ApiResponse.success(JobPageResponse.from(jobQueryService.search(condition)));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<JobResponse> get(@PathVariable @Positive Long jobId) {
        return ApiResponse.success(JobResponse.from(jobQueryService.get(jobId)));
    }
}
