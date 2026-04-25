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

## 🔐 Auth Service (Google OAuth2 + JWT)

The system uses a dedicated **auth-service** responsible for authentication and token issuance.

### Authentication flow

1. User is redirected to Google OAuth2
2. After successful login:
    - user is created (if not exists)
    - JWT token is generated
3. Token must be used in all secured requests

## Available endpoints

### Login via Google

```http
GET /oauth2/authorization/google
```

**Example:**

```
http://localhost:9191/oauth2/authorization/google
```

**Response:**

```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "email": "...",
    "role": "ROLE_USER"
  }
}
```

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
