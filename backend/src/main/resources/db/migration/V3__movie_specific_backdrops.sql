-- Only Interstellar should use its still; other seed movies use their own poster.
UPDATE movies SET backdrop_url = poster_url
WHERE id <> 1 AND backdrop_url = '/images/interstellar-backdrop.jpg';
