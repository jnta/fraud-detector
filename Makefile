.PHONY: up down smoke-test test build clean

# Orchestration
up:
	docker compose up -d --build

down:
	docker compose down

# Smoke Tests (k6 via Docker)
smoke-test:
	docker run --rm --network fraud-detector-network -v $$(pwd)/src/main/resources/tests:/tests -w /tests grafana/k6 run smoke.js

# Load Tests (k6 via Docker)
load-test:
	docker run --rm --network fraud-detector-network -v $$(pwd)/src/main/resources/tests:/tests -w /tests grafana/k6 run test.js

# Gradle Tests
test:
	./gradlew test

# Build native binary
build:
	./gradlew nativeCompile

clean:
	./gradlew clean
	docker compose down -v
