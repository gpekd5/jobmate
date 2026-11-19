package com.jobmate.recommendation.application.service;

import com.jobmate.common.exception.CustomException;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 영속 대기 작업을 선점하고 DB 트랜잭션 밖에서 분석한 뒤 버전·동의를 다시 확인한다.
@Component
@RequiredArgsConstructor
public class MatchAnalysisProcessor {

    private final MatchAnalysisRepository analyses;
    private final RecommendationQueryService query;
    private final MatchAnalysisService matching;

    public void process(long id) {
        var pending = analyses.pending().stream().filter(item -> item.id() == id).findFirst();
        if (pending.isEmpty() || !analyses.claim(id)) return;
        var stored = pending.get();
        try {
            var context = query.context();
            var job = query.job(stored.key().jobId());
            if (!query.key(job, context).equals(stored.key())) {
                analyses.fail(id, "FAILED", "STALE_ANALYSIS");
                return;
            }
            var result = matching.analyze(job, context.profile(), context.preferences(), context.profileVersion(), stored.key().modelVersion());
            if (!query.stillCurrent(context)
                    || query.job(job.getId()).getJobVersion() != stored.key().jobVersion()) {
                analyses.fail(id, "FAILED", "STALE_ANALYSIS");
                return;
            }
            analyses.complete(id, result);
        } catch (CustomException exception) {
            String code = exception.getErrorCode().getCode();
            analyses.fail(id, code.equals("AI_BUDGET_EXHAUSTED") ? "BUDGET_EXHAUSTED" : "FAILED", code);
        } catch (RuntimeException exception) {
            analyses.fail(id, "FAILED", "MATCH_ANALYSIS_FAILED");
        }
    }
}
