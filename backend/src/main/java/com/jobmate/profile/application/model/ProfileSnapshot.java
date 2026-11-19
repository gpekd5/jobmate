package com.jobmate.profile.application.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ProfileSnapshot(long profileVersion, boolean confirmed, JsonNode profile,
        JsonNode preferences, long consentVersion) {
}
