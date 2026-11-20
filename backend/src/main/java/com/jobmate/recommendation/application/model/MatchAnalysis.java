package com.jobmate.recommendation.application.model;

import java.time.Instant;
import java.util.List;

public record MatchAnalysis(
        Long jobId, String status, Integer score, String scoreMeaning, String summary,
        List<Requirement> requirements, List<String> strengths, List<String> gaps, List<String> unknowns,
        int evidenceCount, List<String> evaluatedCategories, long profileVersion, long jobVersion,
        String scoringVersion, String modelVersion, String promptVersion, Instant analyzedAt,
        String errorCode
) {

    public record Requirement(String id, String category, String text, String match,
                              String reason, List<String> evidence) {
    }
}
