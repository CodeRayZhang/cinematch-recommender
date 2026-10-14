CREATE TABLE user_profiles (
    user_id BIGINT PRIMARY KEY,
    bio VARCHAR(280) NOT NULL DEFAULT '',
    preferred_genres VARCHAR(160) NOT NULL DEFAULT '',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE recommendation_feedback (
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, movie_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);
CREATE INDEX idx_reviews_movie_user ON reviews(movie_id, user_id);
