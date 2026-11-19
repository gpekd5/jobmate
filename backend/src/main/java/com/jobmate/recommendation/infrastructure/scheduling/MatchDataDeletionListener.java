package com.jobmate.recommendation.infrastructure.scheduling;

import com.jobmate.profile.application.event.ProfileDataDeletedEvent;
import com.jobmate.recommendation.infrastructure.repository.MatchAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MatchDataDeletionListener {

    private final MatchAnalysisRepository analyses;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(ProfileDataDeletedEvent event) {
        analyses.deleteOwner(event.ownerId());
    }
}
