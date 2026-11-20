package com.jobmate.profile.infrastructure.repository;

import com.jobmate.profile.domain.entity.ProfileFileDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileFileDeletionRepository extends JpaRepository<ProfileFileDeletion, Long> {
}
