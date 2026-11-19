package com.jobmate.job.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.presentation.ApiResponse;
import com.jobmate.job.application.command.CreateJobCommand;
import com.jobmate.job.application.service.JobCommandService;
import com.jobmate.job.presentation.request.JobCreateRequest;
import com.jobmate.job.presentation.request.JobPatchRequest;
import com.jobmate.job.presentation.response.JobIdResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev/jobs")
@RequiredArgsConstructor
@Validated
@Profile("local")
public class DevJobController {

    private final JobCommandService jobCommandService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<JobIdResponse>> create(@Valid @RequestBody JobCreateRequest request) {
        Long jobId = jobCommandService.create(new CreateJobCommand(request.toData(true)));
        return ResponseEntity.created(URI.create("/api/v1/jobs/" + jobId))
                .body(ApiResponse.success(new JobIdResponse(jobId)));
    }

    @PatchMapping("/{jobId}")
    public ApiResponse<JobIdResponse> update(
            @PathVariable @Positive Long jobId,
            @Valid @RequestBody JobPatchRequest request
    ) {
        Long id = jobCommandService.update(jobId, request.toCommand(objectMapper));
        return ApiResponse.success(new JobIdResponse(id));
    }
}
