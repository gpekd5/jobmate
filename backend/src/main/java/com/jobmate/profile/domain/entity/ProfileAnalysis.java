package com.jobmate.profile.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "profile_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Long> documentIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Long> portfolioLinkIds;

    @Column(nullable = false)
    private long dataEpoch;

    @Column(nullable = false)
    private long consentVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode result;

    @Column(length = 60)
    private String errorCode;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;

    @Column(length = 120)
    private String modelVersion;

    @Column(length = 60)
    private String promptVersion;

    public static ProfileAnalysis create(List<Long> documents, List<Long> links, ProfileState state,
            String modelVersion, String promptVersion) {
        ProfileAnalysis analysis = new ProfileAnalysis();
        analysis.ownerId = 1;
        analysis.modelVersion = modelVersion;
        analysis.promptVersion = promptVersion;
        analysis.status = AnalysisStatus.PENDING;
        analysis.documentIds = List.copyOf(documents);
        analysis.portfolioLinkIds = List.copyOf(links);
        analysis.dataEpoch = state.getDataEpoch();
        analysis.consentVersion = state.getConsentVersion();
        analysis.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return analysis;
    }

    public boolean reusableFor(List<Long> documents, ProfileState state, String model, String prompt) {
        return status == AnalysisStatus.SUCCEEDED && result != null && portfolioLinkIds.isEmpty()
                && dataEpoch == state.getDataEpoch() && consentVersion == state.getConsentVersion()
                && modelVersion != null && promptVersion != null
                && Objects.equals(modelVersion, model) && Objects.equals(promptVersion, prompt)
                && Set.copyOf(documentIds).equals(Set.copyOf(documents));
    }

    public void run() {
        status = AnalysisStatus.RUNNING;
    }

    public void succeed(JsonNode content) {
        status = AnalysisStatus.SUCCEEDED;
        result = content.deepCopy();
        completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void fail(String code) {
        status = AnalysisStatus.FAILED;
        errorCode = code;
        result = null;
        completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
