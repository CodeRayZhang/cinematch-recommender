CREATE TABLE movies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    original_title VARCHAR(200) NOT NULL,
    release_year INT NOT NULL,
    genres VARCHAR(160) NOT NULL,
    runtime INT NOT NULL,
    rating DOUBLE NOT NULL DEFAULT 0,
    rating_count INT NOT NULL DEFAULT 0,
    director VARCHAR(160) NOT NULL,
    country VARCHAR(100) NOT NULL,
    synopsis TEXT NOT NULL,
    poster_url VARCHAR(1000) NOT NULL,
    backdrop_url VARCHAR(1000) NOT NULL,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_movies_year ON movies(release_year);
CREATE INDEX idx_movies_rating ON movies(rating, rating_count);
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(40) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(10) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE library_entries (
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, movie_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT valid_library_status CHECK (status IN ('WATCHLIST', 'WATCHED'))
);
CREATE TABLE reviews (
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    stars INT NOT NULL,
    body VARCHAR(2000) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, movie_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT valid_stars CHECK (stars BETWEEN 1 AND 5)
);
