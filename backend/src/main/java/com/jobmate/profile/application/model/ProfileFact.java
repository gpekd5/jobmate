package com.jobmate.profile.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfileFact(
        @NotBlank @Size(max = 200) String name,
        @NotNull Context context,
        @NotNull @Size(max = 1000) String summary,
        @NotNull @Size(max = 1000) String evidence,
        @NotNull Confidence confidence
) {

    public enum Context {
        WORK, TEAM_PROJECT, PERSONAL_PROJECT, TRAINING_PROJECT, LEARNING, UNKNOWN
    }

    public enum Confidence {
        HIGH, MEDIUM, LOW, UNKNOWN
    }
}
