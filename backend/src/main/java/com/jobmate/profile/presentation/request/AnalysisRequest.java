package com.jobmate.profile.presentation.request;

import com.jobmate.profile.application.command.AnalyzeProfileCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AnalysisRequest(
        @NotNull @Size(min = 1, max = 10) List<@NotNull @Positive Long> documentIds,
        @Size(max = 5) List<@NotNull @Positive Long> portfolioLinkIds,
        @NotBlank String analysisMode
) {

    public AnalyzeProfileCommand toCommand() {
        return new AnalyzeProfileCommand(documentIds, portfolioLinkIds == null ? List.of() : portfolioLinkIds, analysisMode);
    }
}
