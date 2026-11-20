ALTER TABLE profile_analysis
    ADD COLUMN model_version VARCHAR(120) NULL,
    ADD COLUMN prompt_version VARCHAR(60) NULL;
