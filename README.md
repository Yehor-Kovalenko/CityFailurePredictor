## Prerequisites
- Java 21
- Docker and Docker compose installed

## General configuration steps
```bash
docker compose down
docker compose up -d --build
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
2. Open base url of the desired service and append `/swagger-ui.html`

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
| Prometheus    | http://localhost:9090                     |
| Grafana       | http://localhost:3001                     |
| Auth metrics  | http://localhost:8083/actuator/prometheus |

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
| `ci.yml` | PR to `main`, push to `main`, manual | Build, tests, container images, system validation |
| `sonarq.yml` | PR to `main`, push to `main`, manual | Static code analysis with Quality Gate |
| `jira-*.yml` | Branch created, PR opened, PR merged | Moves Jira issues: In Progress → Code Review → Done |

```text
Pull request to main               Push to main (merge)
        ↓                                  ↓
Detect changed components          Backend · AI · Frontend (all)
        ↓                                  ↓
Backend · AI · Frontend            Build & push images (sha-<commit>)
        ↓                                  ↓
CI result                          Whole-system smoke test
                                           ↓
                                   Observability check
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
| AI service (Python) | `pytest` with `pytest-cov` |
| Frontend | ESLint, Vitest with coverage, type check and production build |

* A failing test fails the job, and the **CI result** check (required for merging) fails with it.
* On pull requests only the changed components are built; outdated PR runs are cancelled.
* Test reports appear as checks on the PR, coverage in the run **Summary**, and raw reports as `*-test-reports` artifacts (kept for 14 days).

---

## 🔍 Code Quality (SonarQube)

Every PR and push to `main` is analysed by SonarQube Cloud (`sonar-project.properties`). A second job waits for the **Quality Gate** result and fails the workflow if the gate is not passed.

---

## 🐳 Container Images

On every push to `main` all 11 images (Spring services, AI service, frontend) are built with Docker Buildx and pushed to GitHub Container Registry: `ghcr.io/<owner>/<service>`.

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
| Smoke test | `tests/ci/system-smoke.sh` | All services registered in Eureka, gateway health `UP`, gateway rejects requests without a token (401), frontend, AI service and incident-service respond, config-server serves the config repo |
| Observability check | `tests/ci/observability-check.sh` | Every service exposes `/actuator/prometheus`, Prometheus scrapes all targets, gateway traffic shows up in `http_server_requests`, logs of every service reach Loki, a request can be traced by `X-Correlation-ID` |
| Load test | `tests/ci/load-test.sh` | Runs the JMeter plan from `tests/load_tests` at CI scale; fails if errors ≥ 1%, p95 ≥ 1000 ms or p99 ≥ 3000 ms |

Results are shown in the run **Summary** and uploaded as `observability-report` and `load-test-report` artifacts. If anything fails, container logs are uploaded as `system-smoke-logs`.

To run the checks locally against a running system:

```bash
bash tests/ci/system-smoke.sh
bash tests/ci/observability-check.sh   # needs: docker compose up -d prometheus loki alloy
bash tests/ci/load-test.sh             # macOS: LOAD_HOST=host.docker.internal JMETER_NETWORK=bridge
```

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
