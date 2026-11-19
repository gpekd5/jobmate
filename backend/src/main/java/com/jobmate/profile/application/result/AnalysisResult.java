package com.jobmate.profile.application.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobmate.profile.domain.entity.AnalysisStatus;
import com.jobmate.profile.domain.entity.ProfileAnalysis;
import java.time.Instant;
import java.util.List;

public record AnalysisResult(Long analysisId, AnalysisStatus status, JsonNode result,
        String errorCode, Instant createdAt, Instant completedAt, List<Long> documentIds, List<Long> portfolioLinkIds, String modelVersion, String promptVersion) {

    public static AnalysisResult from(ProfileAnalysis analysis) {
        return new AnalysisResult(analysis.getId(), analysis.getStatus(), analysis.getResult(),
                analysis.getErrorCode(), analysis.getCreatedAt(), analysis.getCompletedAt(),
                analysis.getDocumentIds(), analysis.getPortfolioLinkIds(), analysis.getModelVersion(), analysis.getPromptVersion());
    }
}
