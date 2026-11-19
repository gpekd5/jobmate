CREATE TABLE ai_budget (
    budget_id BIGINT NOT NULL PRIMARY KEY,
    reserved_usd DECIMAL(14,8) NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO ai_budget(budget_id, reserved_usd) VALUES (1, 0);

CREATE TABLE ai_usage (
    usage_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purpose VARCHAR(30) NOT NULL,
    model VARCHAR(100) NOT NULL,
    reserved_usd DECIMAL(14,8) NOT NULL,
    state VARCHAR(20) NOT NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_ai_usage_created (created_at)
) ENGINE=InnoDB;
