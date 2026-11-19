package com.jobmate.recommendation.application.service;

import com.jobmate.common.ai.AiGateway;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import com.jobmate.recommendation.domain.policy.CandidatePreferencePolicy;
import com.jobmate.recommendation.domain.policy.MatchScoringPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationCommandService {

    private final RecommendationQueryService query;
    private final MatchAnalysisService matching;
    private final MatchAnalysisRepository analyses;
    private final AiGateway gateway;

    public AnalysisRequest request(long jobId) {
        var context = query.context();
        Job job = query.job(jobId);
        var key = query.key(job, context);
        var cached = analyses.find(key);
        if (cached.isPresent() && !cached.get().status().equals("FAILED")) return response(jobId, cached.get());
        boolean insufficient = !matching.hasSufficientJd(job) || !matching.hasProfileEvidence(context.profile());
        if (!insufficient) gateway.assertAvailable(AiPurpose.JOB_MATCHING);
        return response(jobId, analyses.reserve(key, false, insufficient));
    }

    // 한 회 최대 10건 안에서 우선 후보 80%, 추가 탐색 20%를 선택하고 잔여 슬롯을 재배정한다.
    public AnalysisRequest refresh() {
        var context = query.context();
        gateway.assertAvailable(AiPurpose.JOB_MATCHING);
        List<Job> priority = new ArrayList<>();
        List<Job> exploration = new ArrayList<>();
        var candidates = analyses.uncachedCandidates(context.profileVersion(), context.consentVersion(),
                gateway.modelVersion(), MatchAnalysisService.PROMPT_VERSION, MatchScoringPolicy.VERSION);
        for (Long jobId : candidates) {
            Job job = query.job(jobId);
            if (preferred(job, context)) priority.add(job);
            else exploration.add(job);
        }
        List<Job> selected = new ArrayList<>();
        selected.addAll(priority.subList(0, Math.min(8, priority.size())));
        selected.addAll(exploration.subList(0, Math.min(2, exploration.size())));
        for (Job job : priority) if (selected.size() < 10 && !selected.contains(job)) selected.add(job);
        for (Job job : exploration) if (selected.size() < 10 && !selected.contains(job)) selected.add(job);
        Long last = null;
        for (Job job : selected) {
            var saved = analyses.reserve(query.key(job, context), true,
                    !matching.hasSufficientJd(job) || !matching.hasProfileEvidence(context.profile()));
            if (saved == null) break;
            last = saved.id();
        }
        return new AnalysisRequest(null, last == null ? "UNCHANGED" : "PENDING",
                last == null ? null : "match-" + last);
    }

    private boolean preferred(Job job, RecommendationQueryService.Context context) {
        return CandidatePreferencePolicy.preferred(job.data(), context.preferences());
    }

    private AnalysisRequest response(long jobId, MatchAnalysisRepository.StoredAnalysis stored) {
        return new AnalysisRequest(jobId, stored.status().equals("RUNNING") ? "PENDING" : stored.status(), "match-" + stored.id());
    }

    public record AnalysisRequest(Long jobId, String status, String requestId) {
    }
}
