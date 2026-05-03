-- Cross-service tables that catalog-service queries via JdbcTemplate
-- but does not own as JPA entities (real owners: auth-service / shared social tables).

CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(255) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    profile_url VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS artist_following (
    user_id      VARCHAR(255) NOT NULL,
    artist_id    VARCHAR(255) NOT NULL,
    follow_order INT          NOT NULL,
    PRIMARY KEY (user_id, artist_id)
);
