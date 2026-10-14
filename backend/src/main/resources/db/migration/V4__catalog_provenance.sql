ALTER TABLE movies ADD COLUMN data_source VARCHAR(24) NOT NULL DEFAULT 'manual';
ALTER TABLE movies ADD COLUMN source_id VARCHAR(80);
ALTER TABLE movies ADD COLUMN source_url VARCHAR(500);
ALTER TABLE movies ADD COLUMN source_snapshot VARCHAR(32);
UPDATE movies SET data_source = 'demo' WHERE id BETWEEN 1 AND 18;
CREATE UNIQUE INDEX idx_movies_source_url ON movies(source_url);
