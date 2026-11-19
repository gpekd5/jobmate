package com.jobmate.profile.application.service;

import com.jobmate.profile.application.model.ProfileSnapshot;
import com.jobmate.profile.infrastructure.repository.ProfileStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileQueryService {

    private final ProfileStateRepository stateRepository;

    public ProfileSnapshot current() {
        var state = stateRepository.findById(1L).orElseThrow();
        return new ProfileSnapshot(state.getProfileVersion(), state.isConfirmed(), state.getProfile().deepCopy(),
                state.getPreferences().deepCopy(), state.getConsentVersion());
    }
}
