package com.jobmate.profile.infrastructure.repository;

import com.jobmate.profile.domain.entity.PortfolioLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioLinkRepository extends JpaRepository<PortfolioLink, Long> {

    List<PortfolioLink> findAllByOwnerIdOrderByIdAsc(long ownerId);

    Optional<PortfolioLink> findByIdAndOwnerId(Long id, long ownerId);
}
