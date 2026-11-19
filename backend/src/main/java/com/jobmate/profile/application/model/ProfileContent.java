package com.jobmate.profile.application.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.Stream;

public record ProfileContent(
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> workExperiences,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> skills,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> projects,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> education,
        @NotNull @Size(max = 100) List<@NotNull @Valid ProfileFact> certifications
) {

    public Stream<ProfileFact> facts() {
        return Stream.of(workExperiences, skills, projects, education, certifications).flatMap(List::stream);
    }
}
