CREATE TABLE match_analysis (
    analysis_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    profile_version BIGINT NOT NULL,
    job_version BIGINT NOT NULL,
    consent_version BIGINT NOT NULL,
    model_version VARCHAR(150) NOT NULL,
    prompt_version VARCHAR(30) NOT NULL,
    scoring_version VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    score INT NULL,
    result_json JSON NULL,
    error_code VARCHAR(80) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT uk_match_cache UNIQUE (owner_id, job_id, profile_version, job_version,
        consent_version, model_version, prompt_version, scoring_version),
    INDEX idx_match_pending (status, created_at),
    CONSTRAINT fk_match_job FOREIGN KEY (job_id) REFERENCES job(job_id)
);

CREATE TABLE match_daily_usage (
    owner_id BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    manual_count INT NOT NULL DEFAULT 0,
    automatic_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (owner_id, usage_date)
);
