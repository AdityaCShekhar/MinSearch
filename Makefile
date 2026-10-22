.PHONY: help clean build test compile auth-run auth-package auth-docker up down compose-config bench-corpus bench-jmh-build bench-jmh bench-api

help:
	@echo "Available targets:"
	@echo "  make build          - build all modules"
	@echo "  make test           - run all tests"
	@echo "  make compile        - compile all modules without tests"
	@echo "  make clean          - remove build output"
	@echo "  make auth-run       - run the auth service locally"
	@echo "  make auth-package   - package the auth service jar"
	@echo "  make auth-docker    - build the auth service Docker image"
	@echo "  make up             - start the docker compose stack"
	@echo "  make down           - stop the docker compose stack"
	@echo "  make compose-config - validate docker compose configuration"
	@echo "  make bench-corpus   - generate the deterministic 100,000-document corpus"
	@echo "  make bench-jmh      - build and run JMH with allocation profiling"
	@echo "  make bench-api      - run isolated HTTP benchmarks at 1k/10k/100k documents"

clean:
	./mvnw clean

build:
	./mvnw clean package

test:
	./mvnw test

compile:
	./mvnw -DskipTests compile

auth-run:
	./mvnw -pl services/auth-service -am spring-boot:run

auth-package:
	./mvnw -pl services/auth-service -am -DskipTests package

auth-docker:
	docker build -f services/auth-service/Dockerfile .

compose-config:
	docker compose --env-file .env -f infrastructure/docker/docker-compose.yml config

up:
	docker compose --env-file .env -f infrastructure/docker/docker-compose.yml up --build

down:
	docker compose --env-file .env -f infrastructure/docker/docker-compose.yml down

bench-corpus:
	python3 benchmarks/corpus.py

bench-jmh-build:
	./mvnw -Pbenchmarks -pl benchmarks/jmh -am -DskipTests package

bench-jmh: bench-jmh-build
	mkdir -p benchmarks/output
	java -jar benchmarks/jmh/target/benchmarks.jar -prof gc -rf json -rff benchmarks/output/jmh.json

bench-api:
	python3 -u benchmarks/run.py
