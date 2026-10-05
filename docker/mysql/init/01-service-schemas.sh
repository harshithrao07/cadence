#!/bin/sh
# Database-per-service: one schema per service, and one MySQL user per service that can only reach its own schema.
#
# Runs automatically on the FIRST start of an empty MySQL volume (docker-entrypoint-initdb.d). For an existing
# volume, run it once by hand; it is idempotent:
#   docker compose exec mysql sh /docker-entrypoint-initdb.d/01-service-schemas.sh
#
# Passwords come from the mysql container's environment (see compose.yaml; defaults are for local development).
set -eu

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS auth_db      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS catalog_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS playlist_db  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS streaming_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'auth_svc'@'%'      IDENTIFIED BY '${AUTH_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'catalog_svc'@'%'   IDENTIFIED BY '${CATALOG_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'playlist_svc'@'%'  IDENTIFIED BY '${PLAYLIST_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'streaming_svc'@'%' IDENTIFIED BY '${STREAMING_DB_PASSWORD}';

-- Keep passwords in sync if they were changed in the environment.
ALTER USER 'auth_svc'@'%'      IDENTIFIED BY '${AUTH_DB_PASSWORD}';
ALTER USER 'catalog_svc'@'%'   IDENTIFIED BY '${CATALOG_DB_PASSWORD}';
ALTER USER 'playlist_svc'@'%'  IDENTIFIED BY '${PLAYLIST_DB_PASSWORD}';
ALTER USER 'streaming_svc'@'%' IDENTIFIED BY '${STREAMING_DB_PASSWORD}';

-- Each service may do anything inside its own schema (ddl-auto needs CREATE/ALTER/INDEX) and nothing elsewhere.
GRANT ALL PRIVILEGES ON auth_db.*      TO 'auth_svc'@'%';
GRANT ALL PRIVILEGES ON catalog_db.*   TO 'catalog_svc'@'%';
GRANT ALL PRIVILEGES ON playlist_db.*  TO 'playlist_svc'@'%';
GRANT ALL PRIVILEGES ON streaming_db.* TO 'streaming_svc'@'%';
FLUSH PRIVILEGES;
SQL

echo "Service schemas and users are in place."
