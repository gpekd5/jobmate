package com.jobmate.recommendation.presentation.response;

import com.jobmate.recommendation.application.model.RecommendationPageResult;
import java.util.List;

public record RecommendationPageResponse(List<RecommendationPageResult.Item> items, int page, int size,
                                         long totalElements, int totalPages, boolean hasNext) {

    public static RecommendationPageResponse from(RecommendationPageResult result) {
        return new RecommendationPageResponse(result.items(), result.page(), result.size(), result.totalElements(),
                result.totalPages(), result.hasNext());
    }
}
