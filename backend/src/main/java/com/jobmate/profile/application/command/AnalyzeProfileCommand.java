package com.jobmate.profile.application.command;

import java.util.List;

public record AnalyzeProfileCommand(List<Long> documentIds, List<Long> portfolioLinkIds, String analysisMode) {
}
