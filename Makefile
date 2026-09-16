# ---------------------------------------------------------------------------
# Patient Management Application - development commands.
#
#   make            list every target
#   make dev        run the whole stack
#   make test       run both test suites
#
# Written for GNU Make 3.81, the version macOS ships, so no .ONESHELL (3.82+)
# and no ::= assignment. Recipes use backslash continuations where a single
# shell is needed.
# ---------------------------------------------------------------------------

SHELL := /bin/bash
.DEFAULT_GOAL := help

BACKEND  := backend
FRONTEND := frontend
MVNW     := ./mvnw

API_PORT     := 8080
WEB_PORT     := 4200
SWAGGER_PORT := 8081

API_URL     := http://localhost:$(API_PORT)
WEB_URL     := http://localhost:$(WEB_PORT)
SWAGGER_URL := http://localhost:$(SWAGGER_PORT)

# Quiet Maven down to warnings and test results for the interactive targets;
# the CI target keeps the full log.
MVN_QUIET := -B -Dstyle.color=never

CYAN  := \033[36m
BOLD  := \033[1m
DIM   := \033[2m
RESET := \033[0m

##@ Getting started

.PHONY: help
help: ## Show this help
	@printf "\n$(BOLD)Patient Management$(RESET)  $(DIM)make <target>$(RESET)\n"
	@awk 'BEGIN {FS = ":.*##"} \
		/^##@/ { printf "\n$(BOLD)%s$(RESET)\n", substr($$0, 5); next } \
		/^[a-zA-Z_0-9-]+:.*?##/ { printf "  $(CYAN)%-16s$(RESET) %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@printf "\n$(DIM)API $(API_URL)  |  Web $(WEB_URL)  |  Swagger $(SWAGGER_URL)$(RESET)\n\n"

.PHONY: install
install: $(FRONTEND)/node_modules ## Install front end dependencies
	@printf "Front end dependencies are up to date.\n"

# A directory target, so npm only reruns when the lockfile actually changes.
# `touch` bumps the directory's timestamp past the lockfile's, which is what
# tells make it is satisfied.
$(FRONTEND)/node_modules: $(FRONTEND)/package-lock.json
	cd $(FRONTEND) && npm ci
	@touch $(FRONTEND)/node_modules

##@ Run

.PHONY: dev
dev: install ## Run the API, the web app and Swagger UI together (Ctrl+C stops all)
	@$(MAKE) --no-print-directory swagger-up \
		|| printf "$(DIM)Swagger UI not started (is Docker running?). Continuing without it.$(RESET)\n"
	@printf "\n$(BOLD)API$(RESET) $(API_URL)   $(BOLD)Web$(RESET) $(WEB_URL)   $(BOLD)Swagger$(RESET) $(SWAGGER_URL)\n"
	@printf "$(DIM)Ctrl+C stops the API and the web app. Swagger UI keeps running: make swagger-down$(RESET)\n\n"
	@trap 'kill 0' EXIT INT TERM; \
	 ( cd $(BACKEND) && $(MVNW) $(MVN_QUIET) spring-boot:run ) & \
	 ( cd $(FRONTEND) && npm start ) & \
	 wait

.PHONY: api
api: ## Run the Spring Boot service only
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) spring-boot:run

.PHONY: web
web: install ## Run the Angular dev server only
	cd $(FRONTEND) && npm start

.PHONY: stop
stop: swagger-down ## Stop the API, the web app and the Swagger UI container
	@pkill -f 'spring-boot:run' 2>/dev/null && printf "Stopped the API (spring-boot:run).\n" || true
	@pkill -f 'patient-service-.*\.jar' 2>/dev/null && printf "Stopped the API (jar).\n" || true
	@pkill -f 'angular.*ng.*serve' 2>/dev/null && printf "Stopped the web app.\n" || true
	@printf "Done.\n"

##@ Test

.PHONY: test
test: test-api test-web ## Run both test suites

.PHONY: test-api
test-api: ## Run the back end tests
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) test

.PHONY: test-web
test-web: install ## Run the front end tests once
	cd $(FRONTEND) && npm run test:ci

.PHONY: test-watch
test-watch: install ## Run the front end tests in watch mode
	cd $(FRONTEND) && npm test

.PHONY: test-one
test-one: ## Run one back end test class, e.g. make test-one T=PatientServiceTest
	@test -n "$(T)" || { printf "Usage: make test-one T=PatientServiceTest\n"; exit 2; }
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) test -Dtest=$(T)

##@ Build

.PHONY: build
build: build-api build-web ## Build both applications

.PHONY: build-api
build-api: ## Package the back end as a runnable jar
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) package

.PHONY: build-web
build-web: install ## Build the production front end bundle
	cd $(FRONTEND) && npm run build

.PHONY: run-jar
run-jar: build-api ## Run the packaged jar instead of the Maven plugin
	java -jar $(BACKEND)/target/patient-service-0.0.1-SNAPSHOT.jar

.PHONY: verify
verify: ## Everything a CI pipeline would run: clean build plus both suites
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) clean verify
	cd $(FRONTEND) && npm ci && npm run test:ci && npm run build

##@ Quality

.PHONY: format
format: install ## Format the front end sources with Prettier
	cd $(FRONTEND) && npm run format

.PHONY: format-check
format-check: install ## Check front end formatting without writing
	cd $(FRONTEND) && npm run lint:format

##@ Swagger UI (Docker)

.PHONY: swagger-up
swagger-up: ## Start the Swagger UI container
	docker compose up -d
	@printf "Swagger UI on $(SWAGGER_URL) (needs the API running on $(API_PORT)).\n"

.PHONY: swagger-down
swagger-down: ## Stop and remove the Swagger UI container
	@docker compose down 2>/dev/null || true

.PHONY: swagger-logs
swagger-logs: ## Follow the Swagger UI container logs
	docker compose logs -f

.PHONY: swagger-pull
swagger-pull: ## Pull a newer Swagger UI image
	docker compose pull

##@ Utilities

.PHONY: status
status: ## Show what is currently running
	@printf "$(BOLD)%-10s %-26s %s$(RESET)\n" "SERVICE" "URL" "STATUS"
	@$(call probe,API,$(API_URL),$(API_URL)/actuator/health)
	@$(call probe,Web,$(WEB_URL),$(WEB_URL))
	@$(call probe,Swagger,$(SWAGGER_URL),$(SWAGGER_URL))

.PHONY: api-docs
api-docs: ## Print the OpenAPI document
	@curl -fsS -m 5 $(API_URL)/v3/api-docs 2>/dev/null | python3 -m json.tool 2>/dev/null \
		|| { printf "Could not read $(API_URL)/v3/api-docs. Is the API running? Try 'make api'.\n"; exit 1; }

.PHONY: clean
clean: ## Remove all build output
	cd $(BACKEND) && $(MVNW) $(MVN_QUIET) clean
	rm -rf $(FRONTEND)/dist $(FRONTEND)/.angular
	@printf "Removed build output. Run 'make clean-all' to drop node_modules too.\n"

.PHONY: clean-all
clean-all: clean ## Remove build output and node_modules
	rm -rf $(FRONTEND)/node_modules

# $(call probe,label,url-to-show,url-to-probe) - one status line. Never fails the
# target: "not running" is a normal answer here, not an error.
define probe
printf "%-10s %-26s " "$(1)" "$(2)"; \
if curl -fsS -m 2 -o /dev/null "$(3)" 2>/dev/null; then printf "up\n"; else printf "$(DIM)not running$(RESET)\n"; fi
endef
