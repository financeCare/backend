# Local Kubernetes (kind) with autoscaling

```bash
./k8s/local-up.sh      # kind cluster, metrics-server, Postgres + Redis, the backend (builds the image, loads it into kind)
# API on http://localhost:18090/api   (POST /auth/demo-login works: DEMO_SEED_ENABLED=true in this local setup)
kubectl -n financecare get hpa -w
./k8s/local-down.sh    # delete the cluster
```

Files: `kind-config.yaml` (maps the NodePort to the host), `datastores.yaml` (throwaway Postgres/Redis, no persistence),
`backend.yaml` (Deployment, Service, HorizontalPodAutoscaler). The secrets are created by `local-up.sh` with throwaway values.
Needs Docker, `kind` and `kubectl`.

## What the manifest does

- **Probes** use Spring Boot's liveness/readiness groups on the management port (`9081`), not on the public API port.
  A `startupProbe` allows up to 3 minutes for the JVM; an init container waits for Postgres (without it the app exited
  at the first start and Kubernetes restarted it twice).
- **Resources:** request `250m` CPU / `512Mi`, memory limit `768Mi`, no CPU limit (throttling would distort the
  autoscaling numbers); `-XX:MaxRAMPercentage=70` so the heap follows the container limit.
- **Rolling updates** with `maxUnavailable: 0`; container drops all capabilities, no privilege escalation; the image
  runs as an unprivileged user.
- **HPA:** 1 to 4 replicas, target 60% of the CPU request, 60 s scale-down window.

## Autoscaling test (what actually happened)

k6 (`loadtest/k6/api.js`, 80 virtual users, 210 s) against `http://localhost:18090/api`:

| | |
|---|---|
| Replicas | 1 -> 4 within the first minute (event: `New size: 4; reason: cpu resource utilization (percentage of request) above target`) |
| Pods ready | the 3 new pods took about 80 s to become ready (JVM start on a busy machine) |
| Scale in | back to 1 about 4 minutes after the load stopped (`All metrics below target`) |
| Throughput during the test | 244 req/s, p95 801 ms, p99 1.19 s, 0 errors |

## Read this before quoting the numbers

- **Scaling out did not add capacity here.** kind runs the whole cluster, Postgres, Redis, k6 and the 4 JVMs on the
  same laptop CPU, so more pods only share the same cores. The single backend on the host reached 341 req/s with p95
  223 ms (`loadtest/README.md`); this run was slower. What was verified is the mechanism: metrics-server, the HPA
  decision, pod start and readiness, scale-in. What was not: a real throughput gain, which needs separate nodes.
- The first ~80 s ran on one pod while the others were still starting, so latency in that window is high.
- Demo mode, throwaway credentials and an in-cluster database without persistence: local use only.
- Not covered: Ingress/TLS, PodDisruptionBudget, Helm chart, MinIO/RabbitMQ/OCR in the cluster (the backend starts
  without them; slip upload is unavailable in this setup).
