CREATE TABLE recommendation_models (
    id VARCHAR(36) PRIMARY KEY,
    dataset VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    iteration INT NOT NULL DEFAULT 0,
    parameters_json TEXT NOT NULL,
    summary_json TEXT,
    artifact_json MEDIUMTEXT,
    mappings_json MEDIUMTEXT,
    error_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE TABLE recommendation_model_state (
    id INT PRIMARY KEY,
    active_model_id VARCHAR(36),
    FOREIGN KEY (active_model_id) REFERENCES recommendation_models(id) ON DELETE SET NULL
);
INSERT INTO recommendation_model_state(id,active_model_id) VALUES(1,NULL);
