package com.jobmate.common.ai;

public interface AiConsentChecker {

    boolean permits(AiPurpose purpose);

    long version();
}
