.PHONY: build up test smoke down all clean

build:
	docker compose build

up:
	docker compose up -d

smoke:
	docker run --rm -i --network host -u $(shell id -u):$(shell id -g) -v $(CURDIR):/app -w /app grafana/k6:latest run resources/k6/smoke.js

test:
	mkdir -p test
	docker run --rm -i --network host -u $(shell id -u):$(shell id -g) -v $(CURDIR):/app -w /app grafana/k6:latest run resources/k6/test.js

down:
	docker compose down -v

all: build up smoke test down

clean: down
	./gradlew clean --no-daemon
	rm -rf test
