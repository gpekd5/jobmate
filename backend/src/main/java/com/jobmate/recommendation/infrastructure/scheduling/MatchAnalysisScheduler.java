package com.jobmate.recommendation.infrastructure.scheduling;

import com.jobmate.common.exception.CustomException;
import com.jobmate.recommendation.application.service.MatchAnalysisProcessor;
import com.jobmate.recommendation.application.service.RecommendationCommandService;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@Profile("local")
@ConditionalOnProperty(name = "jobmate.recommendation.scheduling-enabled", matchIfMissing = true)
public class MatchAnalysisScheduler {

    private final MatchAnalysisRepository analyses;
    private final MatchAnalysisProcessor processor;
    private final RecommendationCommandService commands;
    private final TaskExecutor executor;
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();

    public MatchAnalysisScheduler(MatchAnalysisRepository analyses, MatchAnalysisProcessor processor,
                                  RecommendationCommandService commands, @Qualifier("aiTaskExecutor") TaskExecutor executor) {
        this.analyses = analyses;
        this.processor = processor;
        this.commands = commands;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${jobmate.recommendation.worker-delay-ms:1500}", initialDelay = 3000)
    public void drain() {
        analyses.recoverStale();
        for (var pending : analyses.pending()) {
            if (!submitted.add(pending.id())) continue;
            try {
                executor.execute(() -> {
                    try {
                        processor.process(pending.id());
                    } finally {
                        submitted.remove(pending.id());
                    }
                });
            } catch (TaskRejectedException exception) {
                submitted.remove(pending.id());
                break;
            }
        }
    }

    @Scheduled(fixedDelayString = "${jobmate.recommendation.automatic-delay-ms:60000}", initialDelay = 15000)
    public void refreshChangedVersions() {
        try {
            commands.refresh();
        } catch (CustomException exception) {
            // 미확정·미동의·미설정은 자동 호출의 정상적인 차단 상태다. 개인 정보는 로그에 남기지 않는다.
        }
    }
}
