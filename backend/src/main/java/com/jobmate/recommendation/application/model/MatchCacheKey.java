package com.jobmate.recommendation.application.model;

public record MatchCacheKey(long jobId, long profileVersion, long jobVersion, long consentVersion,
                            String modelVersion, String promptVersion, String scoringVersion) {
}
