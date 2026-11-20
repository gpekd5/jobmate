package com.jobmate.recommendation.application.model;

import com.jobmate.job.application.result.JobResult;
import java.time.Instant;
import java.util.List;

public record RecommendationPageResult(List<Item> items, int page, int size, long totalElements,
                                         int totalPages, boolean hasNext) {

    public record Item(Long jobId, String title, String companyName, String source, String region,
                       String originalUrl, Instant deadlineAt, boolean active, String detailAvailability,
                       MatchAnalysis match) {

        public static Item from(JobResult job, MatchAnalysis match) {
            return new Item(job.jobId(), job.data().title(), job.data().companyName(), job.source().name(), job.data().region(),
                    job.data().originalUrl(), job.data().deadlineAt(), job.active(), job.data().detailAvailability().name(), match);
        }
    }
}
