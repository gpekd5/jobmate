package com.jobmate.profile.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jobmate.common.ai.AiPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.List;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "profile_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileState {

    @Id
    private Long ownerId;

    @Column(nullable = false)
    private long profileVersion;

    @Column(nullable = false)
    private boolean confirmed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode profile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode preferences;

    @Column(nullable = false)
    private boolean consented;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<AiPurpose> purposes;

    @Column(nullable = false)
    private long consentVersion;

    private Instant consentedAt;

    @Column(nullable = false, length = 30)
    private String consentPolicyVersion;

    @Column(nullable = false)
    private long dataEpoch;

    @Version
    @Column(nullable = false)
    private long lockVersion;

    public void confirm(JsonNode content) {
        profile = content.deepCopy();
        confirmed = true;
        profileVersion++;
    }

    public void changePreferences(JsonNode content) {
        preferences = content.deepCopy();
        profileVersion++;
    }

    public void changeConsent(boolean allowed, List<AiPurpose> selected) {
        consented = allowed;
        purposes = List.copyOf(selected);
        consentVersion++;
        consentedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        consentPolicyVersion = "local-v1";
    }

    public void removePersonalData(boolean clearProfile) {
        dataEpoch++;
        if (clearProfile) {
            confirmed = false;
            var empty = JsonNodeFactory.instance.objectNode();
            for (String section : List.of("workExperiences", "skills", "projects", "education", "certifications")) {
                empty.putArray(section);
            }
            profile = empty;
            profileVersion++;
        }
    }
}
