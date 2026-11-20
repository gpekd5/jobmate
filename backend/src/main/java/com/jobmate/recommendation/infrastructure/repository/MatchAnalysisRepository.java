package com.jobmate.recommendation.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmate.common.exception.CustomException;
import com.jobmate.recommendation.application.model.MatchAnalysis;
import com.jobmate.recommendation.application.model.MatchCacheKey;
import com.jobmate.recommendation.domain.exception.RecommendationErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// DB 행 잠금과 고유 키로 요청·일일 한도를 원자적으로 선점한다. 외부 호출은 이 트랜잭션 밖이다.
@Repository
@RequiredArgsConstructor
public class MatchAnalysisRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Optional<StoredAnalysis> find(MatchCacheKey key) {
        return jdbc.query("""
                SELECT * FROM match_analysis WHERE owner_id=1 AND job_id=? AND profile_version=?
                  AND job_version=? AND consent_version=? AND model_version=? AND prompt_version=? AND scoring_version=?
                """, this::read, key.jobId(), key.profileVersion(), key.jobVersion(), key.consentVersion(),
                key.modelVersion(), key.promptVersion(), key.scoringVersion()).stream().findFirst();
    }

    @Transactional
    public StoredAnalysis reserve(MatchCacheKey key, boolean automatic, boolean insufficient) {
        LocalDate day = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT IGNORE INTO match_daily_usage(owner_id,usage_date) VALUES(1,?)", day);
        jdbc.queryForObject("SELECT manual_count FROM match_daily_usage WHERE owner_id=1 AND usage_date=? FOR UPDATE",
                Integer.class, day);
        var existing = find(key);
        if (existing.isPresent() && (automatic || !existing.get().status().equals("FAILED"))) return existing.get();
        if (!insufficient) {
            String column = automatic ? "automatic_count" : "manual_count";
            int limit = automatic ? 20 : 3;
            int updated = jdbc.update("UPDATE match_daily_usage SET " + column + "=" + column
                    + "+1 WHERE owner_id=1 AND usage_date=? AND " + column + "<?", day, limit);
            if (updated == 0) {
                if (automatic) return null;
                throw new CustomException(RecommendationErrorCode.MATCH_DAILY_LIMIT);
            }
        }
        if (existing.isPresent()) {
            jdbc.update("UPDATE match_analysis SET status='PENDING',error_code=NULL,result_json=NULL,score=NULL,"
                    + "created_at=UTC_TIMESTAMP(6),completed_at=NULL WHERE analysis_id=? AND status='FAILED'", existing.get().id());
            return find(key).orElseThrow();
        }
        jdbc.update("""
                INSERT INTO match_analysis(owner_id,job_id,profile_version,job_version,consent_version,model_version,
                  prompt_version,scoring_version,status,created_at)
                VALUES(1,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))
                """, key.jobId(), key.profileVersion(), key.jobVersion(), key.consentVersion(), key.modelVersion(),
                key.promptVersion(), key.scoringVersion(), insufficient ? "INSUFFICIENT_DATA" : "PENDING");
        return find(key).orElseThrow();
    }

    public List<StoredAnalysis> pending() {
        return jdbc.query("SELECT * FROM match_analysis WHERE owner_id=1 AND status='PENDING' ORDER BY analysis_id LIMIT 10", this::read);
    }

    public List<Long> uncachedCandidates(long profileVersion, long consentVersion, String modelVersion,
                                         String promptVersion, String scoringVersion) {
        return jdbc.queryForList("""
                SELECT j.job_id FROM job j LEFT JOIN match_analysis m
                  ON m.owner_id=1 AND m.job_id=j.job_id AND m.job_version=j.job_version
                  AND m.profile_version=? AND m.consent_version=? AND m.model_version=?
                  AND m.prompt_version=? AND m.scoring_version=?
                WHERE j.active=1 AND (j.deadline_at IS NULL OR j.deadline_at>UTC_TIMESTAMP(6))
                  AND m.analysis_id IS NULL
                ORDER BY j.job_id DESC LIMIT 200
                """, Long.class, profileVersion, consentVersion, modelVersion, promptVersion, scoringVersion);
    }

    public boolean claim(long id) {
        return jdbc.update("UPDATE match_analysis SET status='RUNNING', created_at=UTC_TIMESTAMP(6) WHERE analysis_id=? AND status='PENDING'", id) == 1;
    }

    public void complete(long id, MatchAnalysis result) {
        try {
            jdbc.update("UPDATE match_analysis SET status=?,score=?,result_json=?,completed_at=UTC_TIMESTAMP(6) WHERE analysis_id=? AND status='RUNNING'",
                    result.status(), result.score(), mapper.writeValueAsString(result), id);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Match serialization failed", exception);
        }
    }

    public void fail(long id, String status, String code) {
        jdbc.update("UPDATE match_analysis SET status=?, error_code=?,result_json=NULL,score=NULL,completed_at=UTC_TIMESTAMP(6)"
                + " WHERE analysis_id=? AND status IN ('PENDING','RUNNING')", status, code, id);
    }

    public void recoverStale() {
        // 재시작 후 사용량을 알 수 없는 호출은 자동 재시도하지 않는다.
        jdbc.update("UPDATE match_analysis SET status='FAILED',error_code='ANALYSIS_INTERRUPTED',result_json=NULL,score=NULL,completed_at=UTC_TIMESTAMP(6)"
                + " WHERE status='RUNNING' AND created_at < UTC_TIMESTAMP(6) - INTERVAL 10 MINUTE");
    }

    public void deleteOwner(long ownerId) {
        jdbc.update("DELETE FROM match_analysis WHERE owner_id=?", ownerId);
    }

    private StoredAnalysis read(ResultSet result, int row) throws SQLException {
        MatchCacheKey key = new MatchCacheKey(result.getLong("job_id"), result.getLong("profile_version"),
                result.getLong("job_version"), result.getLong("consent_version"), result.getString("model_version"),
                result.getString("prompt_version"), result.getString("scoring_version"));
        MatchAnalysis analysis = null;
        try {
            String json = result.getString("result_json");
            if (json != null) analysis = mapper.readValue(json, MatchAnalysis.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored match could not be read", exception);
        }
        var completed = result.getTimestamp("completed_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
        return new StoredAnalysis(result.getLong("analysis_id"), key, result.getString("status"), analysis,
                result.getString("error_code"), completed == null ? null : completed.toInstant());
    }

    public record StoredAnalysis(long id, MatchCacheKey key, String status, MatchAnalysis result,
                                 String errorCode, Instant completedAt) {
    }
}
