# gateway-service

The single public-facing process. All client traffic terminates here. Spring Cloud Gateway routes by path prefix to the correct backend service via Eureka load-balancing, validates JWTs once at the edge, and stamps every forwarded request with `X-User-Id` and `X-Gateway-Secret` headers so downstream services can trust the caller.

## Architecture

```mermaid
flowchart TB
    client([Client])
    client --> gw[gateway-service<br/>:8080]

    gw --> filter[AuthenticationGlobalFilter<br/>1. Validate JWT<br/>2. Extract userId<br/>3. Add X-User-Id<br/>4. Add X-Gateway-Secret]

    filter --> router{Path prefix<br/>routing}

    router -->|/auth/**, /app/**,<br/>/oauth2/**, /login/**| auth[auth-service]
    router -->|/api/v1/user/**,<br/>/api/v1/email/**| auth
    router -->|/api/v1/artist/**,<br/>/api/v1/record/**,<br/>/api/v1/song/**,<br/>/api/v1/genre/**,<br/>/api/v1/files/**,<br/>/api/v1/search,<br/>/api/v1/discover| catalog[catalog-service]
    router -->|/api/v1/playlist/**| playlist[playlist-service]
    router -->|/api/v1/stream/**| streaming[streaming-service]

    auth -.via Eureka.- eureka[(discovery-service)]
    catalog -.via Eureka.- eureka
    playlist -.via Eureka.- eureka
    streaming -.via Eureka.- eureka
```

## Request Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Gateway as gateway-service
    participant Filter as AuthenticationGlobalFilter
    participant Eureka
    participant Backend as backend-service
    participant ITF as InternalTrafficFilter

    Client->>Gateway: HTTP request<br/>Authorization: Bearer <jwt>
    Gateway->>Filter: pre-route filter chain
    alt Public route (e.g., /auth/v1/register)
        Filter-->>Gateway: skip JWT check
    else Protected route
        Filter->>Filter: parse + verify JWT signature
        Filter->>Filter: extract userId from claims
        Filter-->>Gateway: continue (or 401)
    end

    Gateway->>Eureka: resolve lb://<service>
    Eureka-->>Gateway: host:port
    Gateway->>Backend: forward request<br/>+ X-User-Id<br/>+ X-Gateway-Secret
    Backend->>ITF: every request
    ITF->>ITF: verify X-Gateway-Secret matches
    alt secret missing or wrong
        ITF-->>Backend: 401 — direct hit attempt
    else secret valid
        ITF-->>Backend: pass through to controller
    end

    Backend-->>Gateway: response
    Gateway-->>Client: response
```

## Routes

Routes live in `config-server/centralconfigs/gateway-service/gateway-service.properties` and are pulled at startup. Path prefix → `lb://<service>`:

| ID | Path predicates | Target |
|---|---|---|
| `auth-service-auth` | `/auth/**`, `/app/**`, `/oauth2/**`, `/login/**` | `lb://auth-service` |
| `auth-service-user` | `/api/v1/user/**`, `/api/v1/email/**` | `lb://auth-service` |
| `catalog-service` | `/api/v1/artist/**`, `/api/v1/record/**`, `/api/v1/song/**`, `/api/v1/genre/**`, `/api/v1/files/**`, `/api/v1/search`, `/api/v1/discover` | `lb://catalog-service` |
| `playlist-service` | `/api/v1/playlist/**` | `lb://playlist-service` |
| `streaming-service` | `/api/v1/stream/**` | `lb://streaming-service` |

Adding or changing a route is a config change in `gateway-service.properties` — restart the gateway to pick it up.

## Authentication & Header Injection

`AuthenticationGlobalFilter` is a `GlobalFilter` (runs on every route). Its job:

1. **Skip JWT check on public routes** — anything under `/auth/**`, `/app/**`, `/oauth2/**`, `/login/**` doesn't require a Bearer token.
2. **Validate JWT** for everything else: parse `Authorization: Bearer <jwt>`, verify signature against `JWT_SECRET_KEY`, check expiry.
3. **Extract `userId`** from the JWT's `userId` claim.
4. **Mutate the downstream request** to add:
   - `X-User-Id: <userId>` — so backend services don't need to re-parse the JWT.
   - `X-Gateway-Secret: <GATEWAY_SECRET>` — proves to backend services that the request came through the gateway.

**Public paths still get `X-Gateway-Secret`** even though they skip the JWT step. The two concerns are separate: "does this request need a user identity" (skipped on public paths) vs "did this come through the gateway" (always required). Without the secret on public paths, OAuth2 flows like `/oauth2/authorization/google` would 403 against the backend's `InternalTrafficFilter`.

If the JWT is missing or invalid on a protected route, the gateway returns `401` without ever touching the backend.

## CORS

`CorsConfig` registers a reactive `CorsWebFilter` — Spring Cloud Gateway's reactive stack ignores the servlet-style `addCorsMappings` config, so the bean approach is required.

- Allowed origins read from `frontend.url` in centralized config (`gateway-service.properties`); defaults to `http://localhost:3000`. Override via `FRONTEND_URL` env var or supply a comma-separated list for multiple origins.
- Methods: `GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD`.
- Exposes `Authorization`, `Content-Type`, `Content-Range`, `Accept-Ranges` so the audio player can read range responses.
- Credentials enabled, 1-hour preflight cache.

**The gateway is the only service that emits CORS headers.** Backend services explicitly disable Spring Security CORS (e.g., `auth-service.SecurityConfig`) — having multiple emitters causes the browser to reject responses with "multiple values in `Access-Control-Allow-Origin`".

## Inter-Service Authentication Boundary

```mermaid
flowchart LR
    subgraph public[Public]
        c[Client<br/>has JWT]
    end
    subgraph private[Internal network — trusted zone]
        gw[gateway-service<br/>JWT validator]
        be[backend service<br/>InternalTrafficFilter]
    end
    c -->|Bearer token| gw
    gw -->|X-User-Id<br/>X-Gateway-Secret| be
    badactor[Direct call<br/>to backend port]:::dropped -. rejected by ITF .- be

    classDef dropped fill:#fee,stroke:#a00
```

Two layers, both required:

- **JWT** — proves the caller is a logged-in user.
- **Gateway secret** — proves the request came through the gateway, not a direct hit on the backend service's port.

## Configuration

Local `application.properties`:

```properties
spring.application.name=gateway-service
server.port=8080
spring.config.import=optional:file:../env.properties,optional:configserver:http://localhost:8888
```

From `centralconfigs/global/application.properties`:

```properties
gateway.secret=${GATEWAY_SECRET}
eureka.client.service-url.defaultZone=${EUREKA_SERVER_URL:http://localhost:8761/eureka}
eureka.client.register-with-eureka=true
eureka.client.fetch-registry=true
```

From `centralconfigs/gateway-service/gateway-service.properties`: the route definitions (table above).

## Local Development

```bash
cd gateway-service
./mvnw spring-boot:run
```

Boot order: discovery-service → config-server → backend services → gateway. The gateway will return `503 Service Unavailable` for any route whose target service hasn't registered with Eureka yet.

## Why Spring Cloud Gateway

Picked over Zuul / Nginx for two reasons:

- **Reactive request pipeline** based on Project Reactor — non-blocking I/O, scales to many concurrent connections without thread-per-request overhead.
- **First-class Spring Cloud integration** — `lb://` resolution via Eureka and `GlobalFilter`s for JWT/header injection are native concepts, no glue code.
