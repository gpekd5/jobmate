package com.jobmate.profile.application.result;

import com.jobmate.profile.domain.entity.DocumentStatus;
import com.jobmate.profile.domain.entity.DocumentType;
import com.jobmate.profile.domain.entity.ProfileDocument;
import java.time.Instant;

public record DocumentResult(Long documentId, DocumentType type, String fileName,
        DocumentStatus analysisStatus, Instant uploadedAt) {

    public static DocumentResult from(ProfileDocument document) {
        return new DocumentResult(document.getId(), document.getType(), document.getFileName(),
                document.getAnalysisStatus(), document.getUploadedAt());
    }
}
