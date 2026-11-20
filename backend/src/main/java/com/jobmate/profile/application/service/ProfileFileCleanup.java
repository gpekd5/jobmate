package com.jobmate.profile.application.service;

import com.jobmate.profile.domain.entity.ProfileFileDeletion;
import com.jobmate.profile.infrastructure.PrivateDocumentStorage;
import com.jobmate.profile.infrastructure.repository.ProfileFileDeletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

// 파일 삭제 의도는 DB와 함께 저장하고, 확정된 삭제만 원본에 반영한다.
@Service
@RequiredArgsConstructor
public class ProfileFileCleanup {

    private final ProfileFileDeletionRepository deletions;
    private final PrivateDocumentStorage storage;
    private final PlatformTransactionManager transactionManager;

    public void schedule(String key) {
        deletions.save(ProfileFileDeletion.of(key));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                drain();
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        drain();
    }

    public void drain() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            for (var deletion : deletions.findAll()) {
                storage.delete(deletion.getStorageKey());
                deletions.delete(deletion);
            }
        });
    }
}
