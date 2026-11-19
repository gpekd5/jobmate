package com.jobmate.common.ai;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiGateway {

    JsonNode complete(AiPurpose purpose, String instructions, String input, JsonNode schema);

    void assertAvailable(AiPurpose purpose);

    String modelVersion();
}
