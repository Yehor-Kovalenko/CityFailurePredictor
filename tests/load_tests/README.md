# Load tests (JMeter)

`stress_test_invcidents_v1.jmx` covers the full incident lifecycle: create → read → update status → resolve.
Input data: `stress_incidents_v1.csv` (everyday city incidents in Lodz, one row per request). The `*_results/` directories
are reports from the incident-service scaling stress tests (1, 3 and 6 replicas).

Plan parameters are JMeter properties (`-J<name>=<value>`). The defaults match the stress test:

| Property | Default | CI |
|---|---|---|
| `threads` | 10000 | 20 |
| `rampUp` (s) | 60 | 10 |
| `loops` | 2 | 10 |
| `host` / `port` | localhost / 9191 (gateway) | localhost / 8082 (incident-service) |
| `incidentsPath` | `/incident-service/incidents` | `/incidents` |
| `csvFile` | `stress_incidents_v1.csv` | — |

Manual stress test with an HTML report:

```bash
jmeter -n -t stress_test_invcidents_v1.jmx -l results.jtl -e -o results_html
```

In CI (the `system-smoke` job) `tests/ci/load-test.sh` runs the plan at a reduced scale and checks thresholds
against the report's `statistics.json`: errors < 1%, p95 < 1000 ms and p99 < 3000 ms for every transaction.
CI calls incident-service directly because the gateway requires a JWT, which the plan does not send.
