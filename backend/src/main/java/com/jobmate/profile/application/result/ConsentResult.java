package com.jobmate.profile.application.result;

import com.jobmate.common.ai.AiPurpose;
import java.util.List;

public record ConsentResult(String provider, List<AiPurpose> purposes, boolean consented, long consentVersion) {
}
