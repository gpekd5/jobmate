package com.jobmate.profile.application.service;

import com.jobmate.common.ai.AiConsentChecker;
import com.jobmate.common.ai.AiPurpose;
import com.jobmate.profile.infrastructure.repository.ProfileStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileConsentService implements AiConsentChecker {

    private final ProfileStateRepository stateRepository;

    @Override
    public boolean permits(AiPurpose purpose) {
        return stateRepository.findById(1L)
                .map(state -> state.isConsented() && state.getPurposes().contains(purpose)).orElse(false);
    }

    @Override
    public long version() {
        return stateRepository.findById(1L).map(state -> state.getConsentVersion()).orElse(0L);
    }
}
