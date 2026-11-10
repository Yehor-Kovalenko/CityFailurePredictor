## Prerequisites
- Java 21
- Docker and Docker compose installed

## General configuration steps
```bash
docker compose down
docker compose up -d --build
```

Only the entry points are published on the host: the frontend (http://localhost:3000), the API gateway
(http://localhost:9191) and Grafana (http://localhost:3001). Databases, Kafka, Eureka, the config-server,
Prometheus, Loki and the services stay inside the docker network. For local debugging, publish them with:

```bash
docker compose -f docker-compose.yml -f docker-compose.debug-ports.yml up -d
```

---

### How to prepare ConfigServer for the first time
In order to access the ConfigServer config registry, ONLY ONCE you need to do this:
1. `ssh-keygen -t ed25519 -C "email"`, where `email` is your github email
2. Visit https://github.com/settings/keys
3. Create new authentication key
4. Copy key from location where you saved generated public key (default `~/.ssh/*.pub`)
5. ssh -T git@github.com

---
## How to access documentation

Documentation from all services are aggregated at the Gateway service. So there is two ways:
1. Open base url of the gateway service and append `/swagger-ui.html`
2. Open `/<service-name>/swagger-ui.html` through the gateway (the services are not published on the host)

Enjoy your Swagger endpoint documentation!

---


# 📊 Observability (Prometheus + Grafana)

This project includes a full **observability stack** based on:

- Prometheus (metrics collection)
- Grafana (visualization)
- Spring Boot Actuator + Micrometer (metrics exposure)

---

## 📍 Access points

| Service        | URL                                       |
|---------------|-------------------------------------------|
| Grafana       | http://localhost:3001                     |
| Prometheus    | http://localhost:9090 (with `docker-compose.debug-ports.yml`) |
| Gateway metrics | http://localhost:9191/actuator/prometheus |

The backend services expose their metrics on the management port `8081`, reachable only inside the docker network.

---

## 🔐 Grafana login

```text
login: admin
password: admin
```

---

## Add Prometheus as Data Source

1. **Go to Grafana**
2. **Navigate to:** `Settings` → `Data Sources` → `Add data source`
3. **Choose:** `Prometheus`
4. **Set URL:** `http://prometheus:9090`
5. **Action:** Click `Save & Test`

---

## 📈 Available Metrics

### 🔑 Auth Service Metrics
| Metric | Description |
| :--- | :--- |
| `auth_login_success_total` | Successful logins |
| `auth_login_failure_total` | Failed logins (tagged by reason) |
| `auth_login_duration_seconds` | Login latency (histogram) |
| `auth_users_count` | Total users |

### 🌐 Gateway Metrics
| Metric | Description |
| :--- | :--- |
| `gateway_requests_duration` | Request duration |
| `gateway_errors_total` | Gateway errors |

### ⚠️ Error Metrics
| Metric | Description |
| :--- | :--- |
| `app_errors_total` | Application errors (tagged by type) |

**Tags:**
* `type=bad_request`
* `type=internal_error`

---

## 🧱 Default Spring Metrics
*Metrics automatically provided by the framework:*

* `http_server_requests_seconds`
* `jvm_memory_used_bytes`
* `system_cpu_usage`
* `hikaricp_connections`

---

## 🔥 Example PromQL Queries

### Error rate
```promql
rate(app_errors_total[1m])
```

### Login failures
```
rate(auth_login_failure_total[1m])
```

### Login success rate
```
rate(auth_login_success_total[1m])
```

### Login latency (P95)
```
histogram_quantile(0.95, rate(auth_login_duration_seconds_bucket[5m]))
```

### HTTP status distribution
```
rate(http_server_requests_seconds_count[1m]) by (status)
```

---

## Logging configuration

Every running service writes logs to the shared volume which then being served by Loki to Grafana. Every log entry should have the `correlationId` that allows to connect log entries from multiple services into one request flow
Here is example log entry:
```json
{
  "message": "Request GET /api/test -> 200",
  "correlationId": "abc-123",
  "service": "api-gateway",
  "level": "INFO"
}
```

Example queries
```
{correlationId="abc-123"} #trace by correlationId
{service-name="api-gateway"} #trace by service logs
```

---

# 🚀 CI/CD (GitHub Actions)

The pipeline lives in `.github/workflows`. We use short-lived feature branches merged into `main` via pull requests.

| Workflow | Trigger | Purpose |
| :--- | :--- | :--- |
| `ci.yml` | PR to `main`, push to `main`, weekly (Mon 05:00 UTC), manual | Build, tests, static analysis with Quality Gate, security checks, container images, system validation |
| `jira-*.yml` | Branch created, PR opened, PR merged | Moves Jira issues: In Progress → Code Review → Done |

```text
Pull request to main               Push to main (merge)
        ↓                                  ↓
Detect changed components          Backend · AI · Frontend (all)
        ↓                          Secret scan · Dockerfile lint · Supply chain
Backend · AI · Frontend            SonarQube Quality Gate
Secret scan · Dockerfile lint              ↓
Supply chain                       Build image → scan image → push (sha-<commit>)
        ↓                                  ↓
SonarQube Quality Gate             Whole-system smoke test
        ↓                                  ↓
CI result                          Observability check
                                           ↓
                                   Load test (JMeter)
                                           ↓
                                   Publish version tags
```

---

## 🧪 Build & Tests

| Component | What runs |
| :--- | :--- |
| Backend (Java) | `./mvnw verify`: build, JUnit tests, JaCoCo coverage |
| AI service (Python) | `ruff check` lint, `pytest` with `pytest-cov` |
| Frontend | ESLint, Vitest, type check and production build |

* A failing test fails the job, and the **CI result** check (required for merging) fails with it.
* On pull requests only the changed components are built; outdated PR runs are cancelled.
* Test reports appear as checks on the PR, backend and AI coverage in the run **Summary** (informational, it never fails the build), and raw reports as `*-test-reports` artifacts (kept for 14 days).

---

## 🔍 Code Quality (SonarQube)

Every PR and push to `main` is analysed by SonarQube Cloud (`sonar-project.properties`) in the `sonarqube-scan` job of `ci.yml`. It runs after the test jobs. Code coverage is not part of the analysis (`sonar.coverage.exclusions=**/*`), so the Quality Gate checks bugs, vulnerabilities, code smells and duplication, not coverage. The scanner waits for the **Quality Gate** result (`sonar.qualitygate.wait`) and fails the job if the gate is not passed:

* on a pull request the **CI result** check fails, so the PR cannot be merged;
* on `main` no image is built or published, because `prepare` depends on `sonarqube-scan`.

---

## 🛡️ Security & Supply Chain

These jobs run on every pull request, every push to `main` and weekly, regardless of which files changed: new CVEs are published every day, so an unchanged dependency can become vulnerable overnight. All of them are part of the required **CI result** check, and images are built only after they pass.

| Job | Tooling | Fails when |
| :--- | :--- | :--- |
| Secret scan | Gitleaks over the **full git history** | A secret is found in any commit (a secret deleted later is still leaked) |
| Dockerfile lint | Hadolint over all 11 Dockerfiles | A warning or error is reported |
| Supply chain | CycloneDX SBOMs → license gate + Grype | A dependency has a denied license, or a vulnerability passes the risk policy below |
| Image scan | Grype on every built image, **before it is pushed** | A vulnerability in the image (OS packages included) passes the risk policy |

### SBOM

One [CycloneDX](https://cyclonedx.org/) SBOM is generated per component from the resolved dependency tree, runtime scope only:

| Component | Generator | File |
| :--- | :--- | :--- |
| Backend (all Maven modules) | `cyclonedx-maven-plugin` (aggregate) | `backend.cdx.json` |
| Frontend | `@cyclonedx/cyclonedx-npm --omit dev` | `frontend.cdx.json` |
| AI service | `cyclonedx-py environment` | `ai-service.cdx.json` |

The SBOMs are both the input for the scans below and a deliverable: they are uploaded with the scan reports as the `supply-chain-reports` artifact (kept for 30 days).

### Risk-based vulnerability gate (EPSS + CISA KEV)

Blocking on CVSS severity alone does not work in practice: a typical Spring Boot service has dozens of "High" CVEs, most of which are never exploited, and teams end up ignoring the gate. `.github/scripts/vuln-gate.py` evaluates every Grype finding against two real-world exploitation signals:

* **[CISA KEV](https://www.cisa.gov/known-exploited-vulnerabilities-catalog)**: the catalog of vulnerabilities known to be exploited in the wild.
* **[EPSS](https://www.first.org/epss/)**: the probability that a CVE will be exploited within the next 30 days.

A finding **blocks** the pipeline when:

* it is listed in CISA KEV, or
* its severity is **High or Critical**, its EPSS score is **≥ 10%**, and a fixed version exists.

Everything else is reported but does not block. The run **Summary** shows blocking findings and the top 10 non-blocking ones ranked by Grype's risk score, with the installed and fixed versions. Blocking findings also appear as error annotations.

A finding that cannot be fixed yet can be accepted in `.grype.yaml` with a `reason`; accepted findings are counted in the summary so they stay visible.

### License compliance

`.github/scripts/license-gate.py` checks every Maven, npm and PyPI package in the SBOMs against `.github/license-policy.json`:

* **Denied**: strong copyleft and non-open-source licenses (GPL, AGPL, SSPL, BUSL, CC-BY-NC, Commons Clause, Hippocratic). A package fails only if **every** license option in its SPDX expression is denied, so `MIT OR GPL-2.0` passes.
* **Allowed**: weak copyleft (LGPL, EPL, MPL) and the GPL Classpath Exception used by the JDK-related libraries.
* **Exceptions**: listed per package with a written reason (currently `react-leaflet`, Hippocratic-2.1).

The summary shows violations, accepted exceptions, packages without license data, and the license distribution.

### Secrets

Gitleaks scans all commits, not only the current files. Findings that were reviewed and are known to be harmless (for example an expired development token) are listed by fingerprint in `.gitleaksignore`; any new finding fails the build. Reports are redacted, so the secret value is never printed in the logs.

### Running the checks locally

```bash
./mvnw -q cyclonedx:makeAggregateBom                              # target/bom.json
docker run --rm -v "$PWD:/repo" anchore/grype:v0.104.0 sbom:/repo/target/bom.json -o json > grype.json
python3 .github/scripts/vuln-gate.py grype.json
python3 .github/scripts/license-gate.py target/bom.json
docker run --rm -v "$PWD:/repo" -w /repo ghcr.io/gitleaks/gitleaks:v8.28.0 git . --redact
ruff check AI
```

---

## 🐳 Container Images

On every push to `main` all 11 images (Spring services, AI service, frontend) are built with Docker Buildx, scanned with Grype and only then pushed to GitHub Container Registry: `ghcr.io/<owner>/<service>`. Images are built only after the tests, the security checks and the SonarQube Quality Gate pass. An image that fails the vulnerability gate is never pushed.

| Tag | When |
| :--- | :--- |
| `sha-<commit>` | Every build (first 12 characters of the commit SHA) |
| `<pom version>-run<run number>` | After the system validation passes |
| `latest` | After the system validation passes |

Version tags are added to the already tested images without rebuilding them.

---

## ✅ System Validation

The whole system is started with `docker compose` from the images built in the same run, then checked by:

| Step | Script | Checks |
| :--- | :--- | :--- |
| Smoke test | `tests/ci/system-smoke.sh` | All services registered in Eureka, gateway health `UP`, gateway rejects requests without a token (401), frontend, AI service and incident-service respond, config-server serves the config repo, internal ports are not published (C3), incident-service accepts only mTLS clients (C5), the gateway rate limits a burst and counts security events (C4) |
| Observability check | `tests/ci/observability-check.sh` | Every service exposes `/actuator/prometheus`, Prometheus scrapes all targets, gateway traffic shows up in `http_server_requests`, logs of every service reach Loki, a request can be traced by `X-Correlation-ID`, security events reach Loki |
| Load test | `tests/ci/load-test.sh` | Runs the JMeter plan from `tests/load_tests` at CI scale against incident-service over mTLS; fails if errors ≥ 1%, p95 ≥ 1000 ms or p99 ≥ 3000 ms |

Checks of internal components run from a throwaway container in the compose network (`tests/ci/lib/in-network.sh`).
Results are shown in the run **Summary** and uploaded as `observability-report` and `load-test-report` artifacts. If anything fails, container logs are uploaded as `system-smoke-logs`.

To run the checks locally against a running system:

```bash
bash tests/ci/system-smoke.sh
bash tests/ci/observability-check.sh   # needs: docker compose up -d prometheus loki alloy
bash tests/ci/load-test.sh
```

---

# 🏭 Industry 4.0 Features

Industry 4.0 elements implemented in the project. Requirement IDs refer to the *Industry 4.0 requirements* document.

| Area | Requirements | Status |
| :--- | :--- | :--- |
| Geolocation | G1–G4 | ✅ Implemented |
| Mobile interface (PWA) | M1–M4 | ⏳ Planned |
| Cybersecurity | C1–C6 | ✅ Implemented |
| Artificial intelligence | A1–A4 | ⏳ Planned |
| Data analysis and IoT | D1–D4 | ⏳ Planned |

## 📍 Geolocation (G1–G4)

Context-aware incident map based on the browser Geolocation API, Leaflet and OpenStreetMap tiles.

- **G1** – after consent, the map shows the user's position and only incidents within a selectable radius (1–10 km).
- **G2** – the incident form fills in the location from GPS; the pin can be corrected on a small map (click or drag).
- **G3** – only the consent decision is stored (`localStorage`, key `city-failure-predictor.geo-consent`, with the consent version and date); the position stays in memory and is sent to the server only as the location of a submitted incident report. Consent can be withdrawn at any time ("Stop using location"), expires after 180 days, is asked again when the consent text changes (`GEO_CONSENT_VERSION`) and is forgotten on logout, so the next user of a shared device is asked again.
- **G4** – without consent, clicking the map selects the area to filter incidents.

Code: `frontend/src/hooks/useGeolocation.ts`, `frontend/src/services/geoConsent.ts`, `frontend/src/utils/geo.ts`, `frontend/src/components/NearbyPanel.tsx`, `frontend/src/components/LocationPicker.tsx`.

> The browser exposes GPS only over HTTPS or `localhost`. For local testing, a location can be simulated in Chrome DevTools → *Sensors*.

## 🔐 Cybersecurity (C3–C5)

- **C3** – roles are enforced on the API gateway (`RouteAuthorizationService`). The services are not published on the host, so the gateway cannot be bypassed; `docker-compose.debug-ports.yml` publishes the internal ports for local debugging only.
- **C4** – the gateway records every rejected request (`AUTHENTICATION_FAILED` 401, `ACCESS_DENIED` 403, `RATE_LIMITED` 429) as a structured log line and in `gateway_security_events_total{type}`; auth-service records `LOGIN_SUCCESS`, `LOGIN_FAILURE` and `ROLE_CHANGED` (failed logins also in `auth_login_failure_total`). In Loki the events carry the `securityEvent` label, e.g. `{securityEvent="ACCESS_DENIED"}`; the *API Gateway* Grafana dashboard shows them. Rate limiting is a per-client-IP token bucket on the gateway (`app.security.rate-limit.*`: burst 60, 20 requests/s), applied before token validation.
- **C5** – traffic from the gateway to the services and between services (Feign) uses mutual TLS. At startup `certs-init` (`security/tls/generate-certs.sh`) creates an internal CA and one certificate per service (CN/SAN = service name); the CA key stays in a volume only that container mounts. With the `mtls` profile (`security/tls/config/`) the services accept only clients with a certificate from that CA, register their https port in Eureka and verify the peer's host name. Health checks and metrics use the separate management port `8081` inside the network. Eureka, the config-server and Kafka still use plaintext inside the docker network.

---

# 🤖 Artificial Intelligence Module

The AI module is implemented as a separate Python-based service located in the dedicated `AI/` directory. Unlike the remaining backend components, which are implemented as Spring Boot microservices, the AI module focuses on machine learning inference and optimization algorithms.

Technologies:

* Python 3.12
* FastAPI
* Apache Kafka
* Pandas
* XGBoost
* Ant Colony Optimization (ACO)

---

## AI Module Architecture

The AI codebase follows a layered architecture:

```text
AI/
├── Domain/
│   ├── Algorithms/
│   ├── Resources/
│   └── Models/
│
├── Execution/
│   ├── Training/
│   └── Inference/
│
├── Orchestration/
│   ├── RequestManager
│   ├── ConfigManager
│   ├── Kafka Consumers
│   ├── Kafka Producers
│   └── Event Mappers
│
└── main.py
```

### Domain Layer

Contains business-oriented AI components:

* machine learning algorithms
* optimization algorithms
* model resources
* configuration files

### Execution Layer

Responsible for model execution:

* model loading
* inference pipelines
* forecasting logic
* route optimization execution

### Orchestration Layer

Responsible for integration with the microservice ecosystem:

* Kafka consumers
* Kafka producers
* request routing
* configuration management
* event mapping

The `RequestManager` acts as the central entry point responsible for dispatching requests to the appropriate AI model.

---

# ⚡ Electricity Consumption Forecasting

The electricity forecasting model predicts future household energy consumption based on historical readings.

The complete prediction flow is fully event-driven:

```text
Data Ingestion Service
        ↓
electricity.readings.raw
        ↓
AI Service
        ↓
predictions.generated
        ↓
Decision Service
        ↓
alerts.created
        ↓
Notification Service
        ↓
Reporting Service
```

The AI Service consumes raw electricity measurements from Kafka, performs inference using an XGBoost model and publishes prediction events back to Kafka.

Generated predictions are automatically evaluated by the Decision Service. High-risk predictions result in alert creation and notification generation.

---

# 🚦 Traffic Forecasting

The traffic forecasting module follows the same architectural pattern as electricity forecasting.

Traffic measurements are expected to be generated by the Data Ingestion Service and delivered through a dedicated Kafka topic.

Planned processing flow:

```text
Data Ingestion Service
        ↓
traffic.readings.raw
        ↓
AI Service
        ↓
traffic.predictions.generated
        ↓
Decision Service
        ↓
alerts.created
```

The AI Service consumes traffic sensor readings, generates traffic forecasts using an XGBoost model and publishes prediction events for further evaluation.

This approach simulates real-world smart city sensor infrastructure where measurements are continuously streamed into the platform.

---

# 🚚 Route Optimization (VRP)

The route optimization module solves the Vehicle Routing Problem (VRP).

It can be used by the frontend or an operator to calculate the optimal order of visiting incidents or service points.
Unlike electricity and traffic forecasting, it is not based on continuous sensor streams, so it is exposed through a synchronous REST API.

The optimization process is executed directly inside the AI Service:

```text
Client Request
        ↓
AI Service
        ↓
ACO Optimizer
        ↓
Optimized Routes
```

The implementation uses an Ant Colony Optimization algorithm to calculate optimized delivery routes and transportation costs.

Example use cases:

* emergency response planning
* utility maintenance routing
* logistics optimization
* city service vehicle scheduling

---

## AI Service Health Check

```http
GET /ai-service/health
```

Example response:

```json
{
  "status": "UP",
  "service": "ai-service",
  "models": [
    "electricity",
    "traffic",
    "vrp"
  ]
}
```

---

## Kafka Topics Used By AI

### Electricity Pipeline

```text
electricity.readings.raw
predictions.generated
```

### Traffic Pipeline

```text
traffic.readings.raw
traffic.predictions.generated
```

### Shared Topics

```text
alerts.created
incidents.created
incidents.updated
```

The AI module integrates with the rest of the platform through Apache Kafka and supports an event-driven architecture for predictive analytics and alert generation.
