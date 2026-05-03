# Cadence

Music streaming backend, split into a Spring Cloud microservices monorepo. Authentication, catalog management (artists/records/songs/genres), playlists, streaming statistics, and email notifications — wired together with a service registry, an API gateway, a centralized config server, and Kafka for asynchronous events.

## Architecture

```
┌──────────────┐         ┌─────────────────┐
│   browser    │────────▶│  gateway-service│  (Spring Cloud Gateway, port 8080)
└──────────────┘         └────────┬────────┘
                                  │ lb://...
       ┌──────────────────────────┼──────────────────────────────┐
       ▼                ▼         ▼            ▼                 ▼
 auth-service    catalog-service  playlist-service  streaming-service  notification-service
 (port 8085)     (port 8084)      (port 8082)       (port 8083)        (port 8081, no HTTP routes)

       ↑                ↑         ↑            ↑                 ↑
       └────────┬───────┴─────────┴────────────┴─────────────────┘
                ▼                                                 ▼
       discovery-service (Eureka, 8761)              config-server (port 8888)
                                                                  ▲
                                                            centralconfigs/
                                                              ├ global/
                                                              └ <service>/
```

- **discovery-service** — Eureka server. Every other service registers here; the gateway uses it for `lb://<service>` routing.
- **config-server** — Spring Cloud Config Server (`native` backend). Serves config from `config-server/centralconfigs/`. Each service pulls its own properties file plus the shared `global/application.properties`.
- **gateway-service** — single public entry point on `:8080`. Routes by path prefix to the right backend service; injects `X-User-Id` and `X-Gateway-Secret` headers.
- **auth-service** — registration, login, JWT issuance, OAuth2 (Google), email-verification token publishing. Owns the `users` table.
- **catalog-service** — artists, records, songs, genres; admin upload to S3; publishes `record_created` events.
- **playlist-service** — user playlists + system "Liked Songs" playlist; consumes `user_created`.
- **streaming-service** — play-history aggregation, trending songs, listener counts.
- **notification-service** — consumes `record_created` and `email_verification` events; sends emails via SMTP.

## Tech Stack

- **Runtime** — Java 17, Spring Boot 3.3.5, Spring Cloud 2023.0.1
- **Persistence** — MySQL 8 + Spring Data JPA (most services); raw `JdbcTemplate` for cross-service joins in `catalog-service`
- **Discovery / Routing** — Eureka, Spring Cloud Gateway, Spring Cloud LoadBalancer
- **Centralized config** — Spring Cloud Config Server (config-server) with native filesystem backend
- **Auth** — Spring Security, JWT (jjwt 0.12), OAuth2 client (Google)
- **Eventing** — Apache Kafka (single broker, KRaft, no Zookeeper)
- **Storage** — AWS S3 SDK (catalog cover art, audio files)
- **Mail** — Jakarta Mail (SMTP)
- **Build** — Maven per service (no parent pom; each service has its own wrapper)
- **Testing** — JUnit 5, Mockito, Spring Boot Test, Testcontainers (MySQL + Kafka)

## Repository Layout

```
cadence/
├── config-server/
│   └── centralconfigs/         # served by config-server (file backend)
│       ├── global/             # applies to every client
│       │   ├── application.properties
│       │   └── application-{dev,qa,prod}.properties
│       ├── auth-service/
│       ├── catalog-service/
│       ├── playlist-service/
│       ├── streaming-service/
│       ├── notification-service/
│       └── gateway-service/
├── discovery-service/          # Eureka server
├── gateway-service/            # Spring Cloud Gateway
├── auth-service/
├── catalog-service/
├── playlist-service/
├── streaming-service/
├── notification-service/
├── cadence-frontend/           # React/Vite client (separate workspace)
├── compose.yaml                # local Kafka + MySQL via Docker Compose
├── env.properties              # local secrets — gitignored, see "Configuration"
├── run-all-tests.sh            # runs unit + IT across every service
├── TESTING.md                  # test layout, IT setup, Docker workarounds
└── README.md
```

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.9+ (wrappers included per service)
- Docker (for local Kafka and MySQL via Compose; also required for integration tests)
- Optional: AWS account (S3), Google OAuth credentials, SMTP account

### 1. Clone

```bash
git clone https://github.com/<your-username>/cadence.git
cd cadence
```

### 2. Start Kafka + MySQL

```bash
docker compose up -d
```

This launches:
- Kafka broker at `localhost:9092` (KRaft mode, single broker, data in `kafka-data` volume)
- MySQL 8.3 at `localhost:3306` with database `cadence`, user `cadence`, password `cadence` (data in `mysql-data` volume)

### 3. Create env.properties

The monorepo expects an `env.properties` at the root containing the secrets that get plugged into config-server's placeholder values (`${DATASOURCE_URL}`, `${JWT_SECRET_KEY}`, etc.). It is **gitignored** — never commit it.

```properties
# JWT
JWT_SECRET_KEY=replace-with-strong-secret-at-least-32-bytes

# Gateway internal traffic protection
GATEWAY_SECRET=any-shared-secret

# MySQL (matches docker compose defaults)
DATASOURCE_URL=jdbc:mysql://localhost:3306/cadence?useSSL=false&serverTimezone=UTC
DATASOURCE_USERNAME=cadence
DATASOURCE_PASSWORD=cadence

# App URLs
FRONTEND_URL=http://localhost:5173
BACKEND_URL=http://localhost:8080

# AWS (optional for local dev)
AWS_ACCESS_KEY=your_access_key
AWS_SECRET_KEY=your_secret_key
AWS_REGION=us-east-1
AWS_S3_BUCKET=your-bucket-name

# Google OAuth (optional)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# SMTP (notification-service)
MAIL_USERNAME=your_smtp_username
MAIL_PASSWORD=your_smtp_password

# Kafka (overrides default localhost:9092)
KAFKA_URL=localhost:9092

# Eureka
EUREKA_SERVER_URL=http://localhost:8761/eureka
```

### 4. Start the services in order

The boot order matters — config-server and discovery must be up before the others can register / fetch config.

```bash
# In separate terminals (each blocks):
(cd discovery-service && ./mvnw spring-boot:run)
(cd config-server     && ./mvnw spring-boot:run)
(cd auth-service      && ./mvnw spring-boot:run)
(cd catalog-service   && ./mvnw spring-boot:run)
(cd playlist-service  && ./mvnw spring-boot:run)
(cd streaming-service && ./mvnw spring-boot:run)
(cd notification-service && ./mvnw spring-boot:run)
(cd gateway-service   && ./mvnw spring-boot:run)
```

Once the gateway is up at `:8080`, all routes are reachable through it. Eureka dashboard: <http://localhost:8761>. Config-server is at <http://localhost:8888> (try `/auth-service/default` to see merged config).

## Configuration

Every backend service has a near-empty `application.properties`:

```properties
spring.application.name=<service>
server.port=<port>
spring.config.import=optional:file:../env.properties,optional:configserver:http://localhost:8888
```

At startup, the service:

1. Loads its own local `application.properties`.
2. Merges in `env.properties` if present (provides secrets).
3. Pulls its config bundle from config-server (`<service>.properties` + `global/application.properties`, plus profile variants if `spring.profiles.active` is set).

The `optional:` prefix on both imports means the service still starts if either file or config-server is missing — useful for tests, which override everything from `application-test.properties` anyway.

### Per-environment profiles

`config-server/centralconfigs/global/` ships with `application-dev.properties`, `application-qa.properties`, and `application-prod.properties` overrides. To activate one:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Per-service profile files (`<service>-<profile>.properties`) live alongside the base file in `centralconfigs/<service>/` if you need overrides scoped to one service in one environment.

## Eventing (Kafka)

Topics are auto-created on producer startup (`spring.kafka.admin.auto-create=true`).

| Topic                | Producer                                             | Consumers                                                                 |
| -------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------- |
| `user_created`       | `auth-service.UserCreatedProducer`                   | `playlist-service.UserCreatedConsumer` (creates Liked-Songs playlist)     |
| `email_verification` | `auth-service.EmailVerificationProducer`             | `notification-service.EmailVerificationConsumer`                          |
| `record_created`     | `catalog-service.RecordCreatedProducer`              | `notification-service.RecordCreatedConsumer` (emails followers)           |

## API Overview

All routes are reachable through the gateway at `:8080`. Most responses are wrapped in an `ApiResponseDTO<T>` envelope.

- **Authentication** (`/auth/v1` → auth-service)
  - POST `/register`, POST `/authenticate`, POST `/validateEmail`
- **App** (`/app/v1` → auth-service)
  - GET `/ping`, GET `/verify-email?token=...`
- **Artists** (`/api/v1/artist` → catalog-service)
  - POST `/upsert` (ADMIN), DELETE `/delete/{id}` (ADMIN)
  - GET `/all?page=&size=&key=`, GET `/{id}`
  - POST `/{id}/follow`, POST `/{id}/unfollow`
  - GET `/{id}/isFollowing`, GET `/{id}/followers`
- **Records** (`/api/v1/record` → catalog-service)
  - POST `/upsert` (ADMIN), DELETE `/delete/{id}` (ADMIN)
  - GET `/all?artistId=`, GET `/{id}`
- **Songs** (`/api/v1/song` → catalog-service)
  - GET `/all?recordId=`, GET `/{id}`
- **Genres** (`/api/v1/genre` → catalog-service)
  - POST `/add` (ADMIN), GET `/all?page=&size=&key=`
- **Playlists** (`/api/v1/playlist` → playlist-service)
  - GET `/all`, POST `/upsert`, GET `/{id}`, DELETE `/{id}`, GET `/{id}/songs`
  - PUT `/{id}/song/{songId}`, DELETE `/{id}/song/{songId}`, PUT `/{id}/like`
- **Streaming stats** (`/api/v1/stream` → streaming-service)
  - GET `/history`, GET `/stats/users/me/top-songs`, GET `/stats/trending`
  - GET `/stats/songs/{id}`, GET `/stats/songs/play-counts`, GET `/stats/listeners`
- **Search & Discover** (`/api/v1` → catalog-service)
  - GET `/search?key=`, GET `/discover`

## Database Schema

Schema is JPA-driven (`spring.jpa.hibernate.ddl-auto=update` in dev). Each service owns its own tables; some tables are read across service boundaries by name (e.g., catalog-service queries `users` and `artist_following` via `JdbcTemplate`).

![Cadence ER Diagram](assets/cadenceDB.png)

## Testing

Each service has unit tests, controller `@WebMvcTest` slice tests, and integration tests using Testcontainers (MySQL, plus Kafka for `auth-service` / `catalog-service` / `notification-service`). To run everything:

```bash
./run-all-tests.sh
```

See [TESTING.md](TESTING.md) for layout, the Docker Desktop on Windows workaround, and the singleton-container pattern.

## Troubleshooting

- **`Connection refused` from a service to Eureka** — discovery-service isn't up yet, or `EUREKA_SERVER_URL` doesn't match. Check <http://localhost:8761>.
- **Service starts but config values are placeholders** — config-server is unreachable. Hit `http://localhost:8888/<service>/default` to verify it's serving. Check `spring.config.import` in the service's `application.properties`.
- **`No qualifying bean of type 'ClientRegistrationRepository'`** — `auth-service` requires `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` in `env.properties` (or via env vars).
- **`Could not find a valid Docker environment` during tests** — see TESTING.md for the Docker Desktop on Windows workaround.
- **Gateway returns 503 for a service** — that service hasn't registered with Eureka yet, or it crashed at startup. Check Eureka dashboard.
- **Kafka `Connection refused`** — `docker compose up -d` not run, or `KAFKA_URL` mismatch.
