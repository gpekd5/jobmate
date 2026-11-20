package com.jobmate.profile.infrastructure.repository;

import com.jobmate.profile.domain.entity.ProfileAnalysis;
import com.jobmate.profile.domain.entity.AnalysisStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileAnalysisRepository extends JpaRepository<ProfileAnalysis, Long> {

    List<ProfileAnalysis> findAllByOwnerIdOrderByIdAsc(long ownerId);

    Optional<ProfileAnalysis> findByIdAndOwnerId(Long id, long ownerId);

    void deleteAllByOwnerId(long ownerId);

    boolean existsByOwnerIdAndStatusIn(long ownerId, List<AnalysisStatus> statuses);
}
