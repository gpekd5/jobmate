package com.jobmate.profile.infrastructure.repository;

import com.jobmate.profile.domain.entity.ProfileState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProfileStateRepository extends JpaRepository<ProfileState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ProfileState state where state.ownerId = 1")
    ProfileState lockLocal();
}
