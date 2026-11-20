package com.jobmate.profile.presentation.request;

import com.jobmate.profile.application.model.ProfileContent;
import com.jobmate.profile.application.model.ProfileFact;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveProfileRequest(
        @NotNull @PositiveOrZero Long profileVersion,
        @NotNull Boolean confirmed,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> workExperiences,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> skills,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> projects,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> education,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> certifications
) {

    public ProfileContent content() {
        return new ProfileContent(workExperiences, skills, projects, education, certifications);
    }
}
