-- Cross-service tables that catalog-service queries via JdbcTemplate
-- but does not own as JPA entities (real owners: auth-service / shared social tables).
-- Drop-and-recreate so schema changes pick up across container reuses.

DROP TABLE IF EXISTS artist_following;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    profile_url VARCHAR(255)
);

CREATE TABLE artist_following (
    user_id      VARCHAR(255) NOT NULL,
    artist_id    VARCHAR(255) NOT NULL,
    follow_order INT          NOT NULL,
    PRIMARY KEY (user_id, artist_id)
);
