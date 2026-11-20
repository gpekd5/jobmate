package com.jobmate.recommendation.presentation.controller;

import com.jobmate.common.presentation.ApiResponse;
import com.jobmate.recommendation.application.model.MatchAnalysis;
import com.jobmate.recommendation.application.service.RecommendationCommandService;
import com.jobmate.recommendation.application.service.RecommendationQueryService;
import com.jobmate.recommendation.presentation.response.RecommendationPageResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Profile("local")
@Validated
public class RecommendationController {

    private final RecommendationQueryService query;
    private final RecommendationCommandService commands;

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationPageResponse> feed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "MATCH_SCORE") String sort,
            @RequestParam(defaultValue = "true") boolean onlyActive,
            @RequestParam(defaultValue = "ALL") String analysisStatus,
            @RequestParam(defaultValue = "ALL") String viewStatus
    ) {
        return ApiResponse.success(RecommendationPageResponse.from(query.feed(page, size, sort, onlyActive, analysisStatus, viewStatus)));
    }

    @GetMapping("/jobs/{jobId}/match")
    public ApiResponse<MatchAnalysis> detail(@PathVariable @Positive long jobId) {
        return ApiResponse.success(query.match(jobId));
    }

    @PostMapping("/jobs/{jobId}/match/analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RecommendationCommandService.AnalysisRequest> request(@PathVariable @Positive long jobId) {
        return ApiResponse.success(commands.request(jobId));
    }

    @PostMapping("/recommendations/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RecommendationCommandService.AnalysisRequest> refresh() {
        return ApiResponse.success(commands.refresh());
    }
}
