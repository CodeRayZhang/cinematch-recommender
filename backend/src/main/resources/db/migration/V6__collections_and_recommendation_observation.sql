CREATE TABLE movie_collections (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    cover_movie_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cover_movie_id) REFERENCES movies(id) ON DELETE SET NULL
);
CREATE INDEX idx_collections_owner ON movie_collections(user_id, updated_at);
CREATE TABLE collection_movies (
    collection_id VARCHAR(36) NOT NULL,
    movie_id BIGINT NOT NULL,
    position INT NOT NULL,
    note VARCHAR(1000) NOT NULL DEFAULT '',
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (collection_id, movie_id),
    FOREIGN KEY (collection_id) REFERENCES movie_collections(id) ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);
CREATE INDEX idx_collection_order ON collection_movies(collection_id, position, movie_id);
CREATE TABLE recommendation_runs (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    algorithm_version VARCHAR(60) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    segment VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_recommendation_runs_user_time ON recommendation_runs(user_id, created_at);
CREATE INDEX idx_recommendation_runs_time ON recommendation_runs(created_at);
CREATE TABLE recommendation_observations (
    run_id VARCHAR(36) NOT NULL,
    movie_id BIGINT NOT NULL,
    position INT NOT NULL,
    exposed_at TIMESTAMP,
    clicked_at TIMESTAMP,
    saved_at TIMESTAMP,
    rated_at TIMESTAMP,
    rating_stars INT,
    PRIMARY KEY (run_id, movie_id),
    FOREIGN KEY (run_id) REFERENCES recommendation_runs(id) ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);
CREATE TABLE catalog_topics (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(80) NOT NULL,
    description VARCHAR(240) NOT NULL DEFAULT '',
    genre VARCHAR(30) NOT NULL DEFAULT '',
    year_from INT NOT NULL DEFAULT 0,
    year_to INT NOT NULL DEFAULT 0,
    max_runtime INT NOT NULL DEFAULT 0,
    position INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO catalog_topics(id,title,description,genre,year_from,year_to,max_runtime,position,enabled) VALUES
('science-fiction-night','今晚，去另一个宇宙','从熟悉的世界出发，走进新的想象。','科幻',0,0,0,0,TRUE),
('animation-afternoon','留一个下午给动画','轻盈的画面，也能装下深刻的故事。','动画',0,0,120,1,TRUE),
('classic-stories','时间留下的好故事','重访 2000 年以前的银幕记忆。','',0,1999,0,2,TRUE);
ALTER TABLE user_profiles ADD COLUMN recommendation_tracking BOOLEAN NOT NULL DEFAULT TRUE;
