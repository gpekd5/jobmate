package com.jobmate.profile.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PreferencesRequest(
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> desiredRoles,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> regions,
        @Size(max = 30) List<@NotBlank @Size(max = 100) String> employmentTypes,
        @PositiveOrZero Long minSalary,
        Boolean avoidDispatch,
        Boolean avoidFrequentTravel
) {

    public PreferencesRequest {
        desiredRoles = desiredRoles == null ? List.of() : List.copyOf(desiredRoles);
        regions = regions == null ? List.of() : List.copyOf(regions);
        employmentTypes = employmentTypes == null ? List.of() : List.copyOf(employmentTypes);
        avoidDispatch = Boolean.TRUE.equals(avoidDispatch);
        avoidFrequentTravel = Boolean.TRUE.equals(avoidFrequentTravel);
    }
}
