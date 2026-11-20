package com.jobmate.profile.presentation.request;

import com.jobmate.common.ai.AiPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ConsentRequest(@NotBlank String provider, @NotNull @Size(max = 2) List<@NotNull AiPurpose> purposes,
        @NotNull Boolean consented) {
}
