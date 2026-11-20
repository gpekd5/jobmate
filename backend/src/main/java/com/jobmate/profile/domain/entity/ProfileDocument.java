package com.jobmate.profile.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false, length = 200)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus analysisStatus;

    @Column(nullable = false)
    private Instant uploadedAt;

    public static ProfileDocument create(DocumentType type, String fileName, String storageKey) {
        ProfileDocument document = new ProfileDocument();
        document.ownerId = 1;
        document.type = type;
        document.fileName = fileName;
        document.storageKey = storageKey;
        document.analysisStatus = DocumentStatus.NOT_ANALYZED;
        document.uploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return document;
    }

    public void mark(DocumentStatus status) {
        this.analysisStatus = status;
    }
}
