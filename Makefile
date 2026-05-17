.PHONY: build up test smoke down all clean stats generate-index

generate-index:
	./gradlew generateIndex --args="resources/references.json.gz fraud.bin legit.bin 512 5" --no-daemon

build:
	docker compose build

up:
	docker compose up -d

smoke:
	mkdir -p test
	(while true; do date '+--- %Y-%m-%d %H:%M:%S ---'; docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" haproxy api1 api2; echo ''; sleep 3; done) > test/smoke_docker_stats.log & STATS_PID=$$!; \
	docker run --rm -i --network host -u $(shell id -u):$(shell id -g) -v $(CURDIR):/app -w /app grafana/k6:latest run resources/k6/smoke.js; \
	EXIT_CODE=$$?; kill $$STATS_PID 2>/dev/null || true; exit $$EXIT_CODE

test:
	mkdir -p test
	(while true; do date '+--- %Y-%m-%d %H:%M:%S ---'; docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" haproxy api1 api2; echo ''; sleep 3; done) > test/docker_stats.log & STATS_PID=$$!; \
	docker run --rm -i --network host -u $(shell id -u):$(shell id -g) -v $(CURDIR):/app -w /app grafana/k6:latest run resources/k6/test.js; \
	EXIT_CODE=$$?; kill $$STATS_PID 2>/dev/null || true; exit $$EXIT_CODE

stats:
	docker stats haproxy api1 api2

down:
	docker compose down -v

all: build up smoke test down

clean: down
	./gradlew clean --no-daemon
	rm -rf test fraud.bin legit.bin index.bin 10000
