package com.jobmate.recommendation.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobmate.common.ai.AiConsentChecker;
import com.jobmate.common.ai.AiGateway;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.common.exception.CustomException;
import com.jobmate.job.application.result.JobResult;
import com.jobmate.job.domain.entity.Job;
import com.jobmate.job.infrastructure.repository.JobRepository;
import com.jobmate.profile.application.service.ProfileQueryService;
import com.jobmate.recommendation.application.model.MatchAnalysis;
import com.jobmate.recommendation.application.model.MatchCacheKey;
import com.jobmate.recommendation.domain.exception.RecommendationErrorCode;
import com.jobmate.recommendation.domain.policy.MatchScoringPolicy;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import com.jobmate.recommendation.application.model.RecommendationPageResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationQueryService {

    private final ProfileQueryService profileQueryService;
    private final AiConsentChecker consentChecker;
    private final AiGateway gateway;
    private final MatchAnalysisRepository analyses;
    private final JobRepository jobs;
    private final JdbcTemplate jdbc;

    public Context context() {
        var snapshot = profileQueryService.current();
        if (!snapshot.confirmed()) throw new CustomException(RecommendationErrorCode.PROFILE_NOT_CONFIRMED);
        if (!consentChecker.permits(AiPurpose.JOB_MATCHING)) {
            throw new CustomException(RecommendationErrorCode.AI_CONSENT_REQUIRED);
        }
        return new Context(snapshot.profileVersion(), snapshot.profile(), snapshot.preferences(), snapshot.consentVersion());
    }

    public boolean stillCurrent(Context context) {
        var current = profileQueryService.current();
        return current.confirmed() && current.profileVersion() == context.profileVersion()
                && current.consentVersion() == context.consentVersion()
                && consentChecker.permits(AiPurpose.JOB_MATCHING)
                && consentChecker.version() == context.consentVersion();
    }

    public Job job(long jobId) {
        return jobs.findById(jobId).orElseThrow(() -> new CustomException(RecommendationErrorCode.JOB_NOT_FOUND));
    }

    public MatchCacheKey key(Job job, Context context) {
        return new MatchCacheKey(job.getId(), context.profileVersion(), job.getJobVersion(), context.consentVersion(),
                gateway.modelVersion(), MatchAnalysisService.PROMPT_VERSION, MatchScoringPolicy.VERSION);
    }

    public MatchAnalysis match(long jobId) {
        var context = context();
        var result = match(job(jobId), context);
        if (!stillCurrent(context)) throw new CustomException(RecommendationErrorCode.AI_CONSENT_REQUIRED);
        return result;
    }

    public MatchAnalysis match(Job job, Context context) {
        var stored = analyses.find(key(job, context));
        if (stored.isPresent() && stored.get().result() != null) return stored.get().result();
        String status = stored.map(item -> item.status().equals("RUNNING") ? "PENDING" : item.status()).orElse("NOT_ANALYZED");
        return new MatchAnalysis(job.getId(), status, null, MatchAnalysisService.SCORE_MEANING,
                switch (status) {
                    case "INSUFFICIENT_DATA" -> "프로필 또는 공고의 검증 가능한 근거가 부족합니다.";
                    case "BUDGET_EXHAUSTED" -> "AI 분석 예산이 부족합니다.";
                    case "FAILED" -> "분석을 완료하지 못했습니다.";
                    case "PENDING" -> "AI 분석 대기 또는 진행 중입니다.";
                    default -> "아직 분석하지 않은 공고입니다.";
                }, List.of(), List.of(), List.of(), List.of(), 0, List.of(), context.profileVersion(),
                job.getJobVersion(), MatchScoringPolicy.VERSION, gateway.modelVersion(), MatchAnalysisService.PROMPT_VERSION,
                stored.map(MatchAnalysisRepository.StoredAnalysis::completedAt).orElse(null),
                stored.map(MatchAnalysisRepository.StoredAnalysis::errorCode).orElse(null));
    }

    public RecommendationPageResult feed(int page, int size, String sort, boolean onlyActive,
                                            String analysisStatus, String viewStatus) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE
                || !List.of("MATCH_SCORE", "NEWEST", "DEADLINE").contains(sort)
                || !List.of("ALL", "ANALYZED", "NOT_ANALYZED").contains(analysisStatus)
                || !"ALL".equals(viewStatus)) {
            throw new CustomException(RecommendationErrorCode.INVALID_RECOMMENDATION_QUERY);
        }
        Context context = context();
        List<Object> arguments = new ArrayList<>(List.of(context.profileVersion(), context.consentVersion(),
                gateway.modelVersion(), MatchAnalysisService.PROMPT_VERSION, MatchScoringPolicy.VERSION));
        String join = """
                FROM job j LEFT JOIN match_analysis m ON m.job_id=j.job_id AND m.owner_id=1
                  AND m.profile_version=? AND m.consent_version=? AND m.model_version=? AND m.prompt_version=?
                  AND m.scoring_version=? AND m.job_version=j.job_version WHERE 1=1
                """;
        if (onlyActive) {
            join += " AND j.active=1 AND (j.deadline_at IS NULL OR j.deadline_at>UTC_TIMESTAMP(6))";
        }
        if (analysisStatus.equals("ANALYZED")) join += " AND m.result_json IS NOT NULL AND m.status IN ('READY','INSUFFICIENT_DATA')";
        if (analysisStatus.equals("NOT_ANALYZED")) join += " AND (m.result_json IS NULL OR m.status NOT IN ('READY','INSUFFICIENT_DATA'))";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + join, Long.class, arguments.toArray());
        String order = switch (sort) {
            case "DEADLINE" -> "j.deadline_at IS NULL,j.deadline_at ASC,j.job_id DESC";
            case "NEWEST" -> "j.posted_at IS NULL,j.posted_at DESC,j.job_id DESC";
            default -> "m.score IS NULL,m.score DESC,j.posted_at IS NULL,j.posted_at DESC,j.job_id DESC";
        };
        arguments.add(size);
        arguments.add(page * size);
        var ids = jdbc.queryForList("SELECT j.job_id " + join + " ORDER BY " + order + " LIMIT ? OFFSET ?", Long.class, arguments.toArray());
        Instant now = Instant.now();
        var items = ids.stream().map(this::job).map(job -> RecommendationPageResult.Item.from(
                JobResult.from(job, now), match(job, context))).toList();
        if (!stillCurrent(context)) throw new CustomException(RecommendationErrorCode.AI_CONSENT_REQUIRED);
        long count = total == null ? 0 : total;
        return new RecommendationPageResult(items, page, size, count, (int) ((count + size - 1) / size),
                (long) (page + 1) * size < count);
    }

    public record Context(long profileVersion, JsonNode profile, JsonNode preferences, long consentVersion) {
    }
}
