package com.jobmate.profile.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortfolioLinkRequest(@NotBlank @Size(max = 300) String url, @Size(max = 500) String description) {
}
