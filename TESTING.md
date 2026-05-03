# Testing

## Layout

Each service has three test slices:

- **Unit tests** (`*Test.java` under `service/`, `filter/`) — pure JUnit 5 + Mockito, no Spring context.
- **Controller tests** (`*ControllerTest.java`) — `@WebMvcTest` slice, mocks the service layer, validates HTTP wiring.
- **Integration tests** (`*IT.java` under `integration/`) — `@SpringBootTest` with Testcontainers for MySQL (and Kafka for `auth-service` and `catalog-service`).

## Running

Per service:

```bash
cd <service> && ./mvnw test
```

All four services in sequence:

```bash
./run-all-tests.sh
```

Skip integration tests (much faster — no Docker needed):

```bash
./run-all-tests.sh -DskipITs
```

## Docker Desktop on Windows — required workarounds

Integration tests need Docker. Docker Desktop 4.56 on Windows ships with a daemon advertising minimum API version 1.44, but the `docker-java` client bundled in Testcontainers defaults to an older version. Two workarounds are wired in:

1. **Surefire env per service** (already in each `pom.xml`, activated by the `docker-desktop-windows` profile when Maven detects `os.family == windows`):
   ```xml
   <environmentVariables>
       <DOCKER_HOST>tcp://localhost:2375</DOCKER_HOST>
   </environmentVariables>
   <systemPropertyVariables>
       <api.version>1.45</api.version>
   </systemPropertyVariables>
   ```
   This requires Docker Desktop's "Expose daemon on tcp://localhost:2375 without TLS" toggle to be **on**.

2. **Per-machine `~/.testcontainers.properties`** (not in the repo — set once on each Windows dev box):
   ```properties
   api.version=1.45
   ```
   Older Testcontainers fall back to this if the JVM system property is missing.

On Linux/macOS CI, neither workaround is needed — the `docker-desktop-windows` profile is inactive, the unix socket is auto-detected, and modern Docker daemons accept the default API version.

## Container reuse across test classes

Each service's `BaseIntegrationTest` starts its containers in a `static {}` block rather than relying on `@Container` lifecycle. Containers run for the entire JVM lifetime so multiple test classes in the same module share one MySQL (and one Kafka) container, avoiding ~10s of restart overhead per class.
