package com.jobmate.profile.infrastructure.repository;

import com.jobmate.profile.domain.entity.ProfileDocument;
import com.jobmate.profile.domain.entity.DocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileDocumentRepository extends JpaRepository<ProfileDocument, Long> {

    List<ProfileDocument> findAllByOwnerIdOrderByIdAsc(long ownerId);

    Optional<ProfileDocument> findByIdAndOwnerId(Long id, long ownerId);

    boolean existsByOwnerIdAndType(long ownerId, DocumentType type);
}
