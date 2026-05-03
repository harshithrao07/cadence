#!/usr/bin/env bash
# Run unit + integration tests across all four backend services.
# Each service is built and tested in turn so a failure in one is easy to spot.
# Use ./run-all-tests.sh -DskipITs to run unit tests only.

set -e

SERVICES=(config-server auth-service catalog-service playlist-service streaming-service notification-service)

for svc in "${SERVICES[@]}"; do
  echo
  echo "======================================================================"
  echo "  $svc"
  echo "======================================================================"
  (cd "$svc" && ./mvnw test -pl . --no-transfer-progress "$@")
done

echo
echo "All services green."
