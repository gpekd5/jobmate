CREATE TABLE profile_state (
    owner_id BIGINT NOT NULL PRIMARY KEY,
    profile_version BIGINT NOT NULL,
    confirmed BIT NOT NULL,
    profile JSON NOT NULL,
    preferences JSON NOT NULL,
    consented BIT NOT NULL,
    purposes JSON NOT NULL,
    consent_version BIGINT NOT NULL,
    consented_at DATETIME(6) NULL,
    consent_policy_version VARCHAR(30) NOT NULL,
    data_epoch BIGINT NOT NULL,
    lock_version BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO profile_state VALUES (
    1, 0, b'0', JSON_OBJECT('workExperiences', JSON_ARRAY(), 'skills', JSON_ARRAY(),
        'projects', JSON_ARRAY(), 'education', JSON_ARRAY(), 'certifications', JSON_ARRAY()),
    JSON_OBJECT('desiredRoles', JSON_ARRAY(), 'regions', JSON_ARRAY(), 'employmentTypes', JSON_ARRAY(),
        'minSalary', NULL, 'avoidDispatch', false, 'avoidFrequentTravel', false),
    b'0', JSON_ARRAY(), 0, NULL, 'local-v1', 0, 0
);

CREATE TABLE profile_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    analysis_status VARCHAR(30) NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_profile_document_storage UNIQUE (storage_key),
    INDEX idx_profile_document_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_link (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    url VARCHAR(300) NOT NULL,
    description VARCHAR(500) NULL,
    INDEX idx_portfolio_link_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profile_analysis (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    document_ids JSON NOT NULL,
    portfolio_link_ids JSON NOT NULL,
    data_epoch BIGINT NOT NULL,
    consent_version BIGINT NOT NULL,
    result JSON NULL,
    error_code VARCHAR(60) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    INDEX idx_profile_analysis_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profile_file_deletion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    storage_key VARCHAR(100) NOT NULL,
    CONSTRAINT uk_profile_file_deletion_key UNIQUE (storage_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
