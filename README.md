# Real-Time Streaming Anomaly Detector

A production-grade, distributed real-time anomaly detection pipeline built with **Python 3.13**, **Spring Boot 4.1.1 (Java 21)**, **AWS SNS**, **AWS SQS**, and **LocalStack**.

```
[ Python Producer ] ───Publish───► ( SNS Topic )
                                        │
                                  Fan-out Sub
                                        ▼
                                 [ SQS Queue ] ──DeadLetterTarget──► [ SQS DLQ ]
                                        │
                                   @SqsListener (Virtual Threads)
                                        ▼
                             [ Java Consumer ] ──► [ O(1) Rolling Window Z-Score ]
```

---

## Foreword

It was a pleasure developing this prototype. Before diving into the technical details, let me share a few of the initial design decisions behind this project:

1. **Demonstrating Technical Breadth and Modern Best Practices**:
   I chose to showcase my skills across different ecosystems by writing the producer in **Python 3.13** and the consumer in **Java 21 with Spring Boot 4.1.1**. I made sure to enforce strict linters, code quality checkers, and static analysis on both sides to guarantee modern, idiomatic code. Certain constructs were introduced deliberately for demonstration purposes—such as using `java.util.concurrent.locks.ReentrantLock` (even knowing that in this specific example a simple `synchronized` block would have sufficed).

2. **Custom High-Performance Data Structure**:
   Along the same lines, I felt it was important to demonstrate the design of a custom data structure. Wanting the consumer to handle high ingestion throughput with minimal overhead, I opted for a static circular buffer (ring buffer) for the rolling window. The standard library alternative would typically be a `Deque`, likely backed by a `LinkedList`. The static ring buffer implementation is simple, memory-efficient, and extremely fast, while also allowing me to maintain running sums of values and squares in $O(1)$ time for immediate rolling variance and standard deviation computation.

3. **Human-Guided AI Collaboration**:
   I naturally leveraged artificial intelligence to accelerate development, review code, and generate docstrings. However, this collaboration was strictly guided and supervised. I carefully selected the formatters and linters, outlined the exact class architecture, designed the LocalStack infrastructure, enforced modern coding conventions, and conducted a thorough review of everything implemented. In Python, I deliberately tailored the Ruff rule suite to prevent the AI from adopting outdated idioms or antipatterns. I also made sure to specify and review critical test scenarios—particularly for the rolling window mechanics and standard deviation calculations. This rigor ensures production-grade quality and provides a dependable self-validation harness for the AI. The AI model used was **Gemini 3.8 Flash**.

I hope you enjoy the result! I am very much looking forward to discussing the architecture, implementation choices, and potential alternatives.

---

## 1. System Architecture

- **Producer (`producer/`)**: Python 3.13 streaming engine generating Gaussian data points with stochastic anomaly injection, publishing to an Amazon SNS topic either individually or in batches.
- **Messaging (LocalStack)**: Emulated AWS infrastructure. An SNS topic publishes to an SQS queue via an SQS subscription configured with `RawMessageDelivery=true`. The main queue is protected by a Dead-Letter Queue (DLQ) with `maxReceiveCount=3`.
- **Consumer (`consumer/`)**: Spring Boot 4.1.1 application on Java 21 leveraging virtual threads. Listens to SQS using Spring Cloud AWS 4.1.1 `@SqsListener`, parses messages via Jackson 3, and feeds values to a thread-safe $O(1)$ `RollingWindow` for Z-score anomaly evaluation.
- **Detection Algorithm**: Points are compared against a rolling window of size $N$ with running sum and sum-of-squares ($O(1)$ amortized with periodic drift recomputation). Points whose $Z = \frac{|x - \mu|}{\sigma} > \text{threshold}$ trigger alerting and are excluded from the baseline by default.

---

## 2. Prerequisites

- **Docker Desktop** (with Compose v2+) running in Linux container mode.
- No local toolchains (Java, Gradle, Python, or uv) required on the host system — everything builds and executes through containerized Docker targets.

---

## 3. Quick Start

Start the entire pipeline with a single command:

```bash
docker compose up --build
```

### Expected Output

1. **LocalStack** creates the messaging topology:
   ```text
   Messaging ready: topic=arn:aws:sns:us-east-1:000000000000:data-points queue=arn:aws:sqs:us-east-1:000000000000:data-points-detector dlq=arn:aws:sqs:us-east-1:000000000000:data-points-detector-dlq
   ```
2. **Producer** begins emitting data points with periodic throughput reporting:
   ```text
   [2026-09-13T16:07:59.371+00:00] INFO [producer] Starting producer loop: topic=arn:aws:sns:us-east-1:000000000000:data-points, mean=100.0, stddev=10.0, batch_size=1, interval=0.500s
   [2026-09-13T16:07:59.399+00:00] INFO [producer] Published id=169ac7d5-3be3-4718-8de7-0d131315aec6 value=91.16
   [2026-09-13T16:08:04.497+00:00] INFO [producer] rate=2.1 msg/s (total=11)
   ```
3. **Consumer** processes incoming points, logging warm-up progress and detecting anomalies:
   ```text
   [2026-09-13T16:09:04.156Z] Data point: 97.28 | Status: WARMUP (1/10) | Z-score: n/a
   ...
   [2026-09-13T16:09:04.157Z] Data point: 103.18 | Status: WARMUP (9/10) | Z-score: n/a
   [2026-09-13T16:09:04.195Z] Data point: 104.48 | Status: OK | Z-score: 0.03
   [2026-09-13T16:09:04.372Z] Data point: 63.05 | Status: ANOMALY DETECTED! | Z-score: 3.38 | ALERT: Significant deviation detected.
   ```

To stop the system gracefully:
```bash
docker compose down
```

---

## 4. Testing & Code Quality Gates

Both services have isolated, reproducible multi-stage Docker test targets:

### Producer Verification
Runs Ruff linter (all rules enabled), Ruff formatter check, Mypy in strict mode, and Pytest unit tests:
```bash
docker build --target test ./producer
```

### Consumer Verification
Runs Spotless Java format check (Google Java Format), SpotBugs static analysis & bug detection, JUnit 5 tests, and JaCoCo coverage verification:
```bash
docker build --target test ./consumer
```

> **Quality Gate**: JaCoCo enforces **100% line and branch coverage** on the core `com.vinigodoy.detector.detection` package and **≥80% line coverage** across the application bundle. SpotBugs enforces zero defects at `MAX` effort level.

---

## 5. Configuration Reference

All settings can be customized via environment variables in `docker-compose.yml` or a `.env` file.

### Producer Settings
| Variable | Default | Description |
|---|---|---|
| `SNS_TOPIC_ARN` | *Required* | Destination SNS topic ARN |
| `PRODUCER_MEAN` | `100.0` | Normal distribution mean |
| `PRODUCER_STDDEV` | `10.0` | Normal distribution standard deviation ($> 0$) |
| `PRODUCER_INTERVAL_SECONDS` | `0.25` | Publication interval ($\ge 0$, default yields ~4 msg/s) |
| `PRODUCER_BATCH_SIZE` | `1` | Messages per batch ($1$ to $10$) |
| `PRODUCER_ANOMALY_PROBABILITY` | `0.02` | Probability of injecting an outlier ($0.0$ to $1.0$) |
| `PRODUCER_ANOMALY_MIN_SIGMAS` | `5.0` | Minimum deviation magnitude for anomalies ($> 1.0$) |
| `PRODUCER_ANOMALY_MAX_SIGMAS` | `8.0` | Maximum deviation magnitude for anomalies ($\ge \text{min}$) |
| `PRODUCER_SEED` | *None* | Optional integer seed for deterministic testing |

### Consumer Settings
| Variable | Default | Description |
|---|---|---|
| `QUEUE_NAME` | `data-points-detector` | Target SQS queue name |
| `DETECTOR_WINDOW_SIZE` | `50` | Maximum observations kept in rolling baseline ($\ge 2$) |
| `DETECTOR_Z_THRESHOLD` | `3.0` | Z-score cutoff for anomaly classification ($> 0.0$) |
| `DETECTOR_MIN_SAMPLES` | `10` | Minimum samples required before scoring begins ($\le \text{window}$) |
| `DETECTOR_INCLUDE_ANOMALIES_IN_WINDOW` | `false` | When false, anomalies are excluded from the baseline |
| `SQS_MAX_CONCURRENT_MESSAGES` | `200` | SQS concurrent message processing limit |
| `SQS_MAX_MESSAGES_PER_POLL` | `10` | Messages retrieved per SQS poll |

### LocalStack Settings
| Variable | Default | Description |
|---|---|---|
| `LOCALSTACK_LS_LOG` | `warn` | Log level for LocalStack (`trace`, `debug`, `info`, `warn`, `error`). Set to `warn` by default to suppress verbose per-request SQS/SNS polling logs |
| `LOCALSTACK_AUTH_TOKEN` | *Configured* | Pro/team auth token for LocalStack activation |
| `LOCALSTACK_IMAGE` | `localstack/localstack:latest` | LocalStack Docker container image |

---

## 6. Architecture & Implementation Decisions

1. **Why SNS Fan-Out to SQS?**
   Decoupling the ingestion entrypoint from the processing queue enables architectural fan-out. Future consumers (e.g., archival storage, real-time dashboards, audit logs) can subscribe independent SQS queues to the topic without altering the producer.
2. **$O(1)$ Ring Buffer with Periodic Drift Recalculation**:
   Rather than maintaining cumulative running sums indefinitely (which can suffer floating-point roundoff drift over millions of updates), the `RollingWindow` updates running sums in $O(1)$ time and periodically recomputes sums directly from buffer elements every $N$ insertions.
3. **Virtual Threads and Lock Safety**:
   `ZScoreDetector` uses `java.util.concurrent.locks.ReentrantLock` rather than synchronized methods, eliminating carrier thread pinning on Java 21 virtual threads.
4. **Modern Java 21 Idioms**:
   Adheres to Java 21 conventions including `record` types, sealed interface hierarchies (`DetectionResult` permitting `Warmup` and `Scored`), pattern-matching switch expressions, and local variable type inference (`var`).
5. **Robust Non-Blocking Logging**:
   The consumer routes high-frequency detection alerts through Logback's `AsyncAppender` with a dedicated UTC time format, ensuring that console I/O never bottlenecks the virtual thread message ingestion loop.

---

## 7. Up Next

### 1. Production Tooling & Maintainability Recommendations

If taking this prototype into a full-fledged enterprise production lifecycle, the following additional tooling would be incorporated:

- **Continuous Integration & Delivery (CI/CD)**:
  - **GitHub Actions / GitLab CI**: Automated pipelines running containerized linting and test stages (`docker build --target test ./producer` and `./consumer`) on every pull request, with layer caching for Gradle (`~/.gradle`) and uv (`~/.cache/uv`).
  - **Automated Dependency Maintenance**: Dependabot or Renovate to continuously monitor and submit automated pull requests for Python and Gradle dependencies.
- **Security & Vulnerability Auditing**:
  - **Container Security Scanning**: Trivy or Grype integrated into CI to scan base images and final application containers for CVEs.
  - **Supply Chain Security**: OWASP Dependency-Check or Snyk for third-party libraries, along with CycloneDX / SPDX Software Bill of Materials (SBOM) generation.
  - **Static Application Security Testing (SAST)**: SonarQube or SonarCloud for code quality gates, technical debt tracking, and security hotspot scanning.
- **Contract & Integration Testing**:
  - **AsyncAPI / Schema Validation**: Formally document the message schema using AsyncAPI or JSON Schema, with automated schema validation on publish and consume.
  - **Testcontainers**: Use Testcontainers for Java (`LocalStackContainer`) and Python to spin up isolated, ephemeral AWS dependencies directly within integration test suites, removing the need for manual pre-provisioned environments.
- **Developer Experience (DX)**:
  - **Taskfile or Justfile**: Standardize cross-platform developer workflows (`task test`, `task run`, `task lint`) across both Python and Java subprojects.
  - **Pre-commit Hooks**: Enforce Ruff, Spotless, and conventional commit message checks (`git-cliff`, `commitlint`) before commits are made.
- **Observability & APM**:
  - **OpenTelemetry (OTel)**: Distributed tracing propagating trace IDs from SNS message attributes through SQS to the consumer's virtual threads.
  - **Prometheus & Grafana**: Export operational metrics via Spring Boot Actuator (`/actuator/prometheus`) and Micrometer (e.g., message lag, processing latency percentiles, anomaly rates).

---

### 2. Deployability to Orchestration Platforms (e.g., Kubernetes)

The application is well-suited for container orchestration platforms like Kubernetes. To transition from Docker Compose to Kubernetes, the following components and operational practices should be added:

- **Kubernetes Manifests / Helm Chart / Kustomize**:
  - `Deployment` specifications for both Producer and Consumer with explicit CPU and memory resource requests/limits.
  - `ConfigMap` and `Secret` resources to externalize environment variables.
  - **External Secrets Operator / AWS Secrets Manager**: Secure credential retrieval instead of static access keys, coupled with **IAM Roles for Service Accounts (IRSA)** or EKS Pod Identities for keyless AWS authentication.
- **Health & Lifecycle Management**:
  - **Spring Boot Actuator Probes**: Expose `/actuator/health/liveness` and `/actuator/health/readiness` for Kubernetes probes.
  - **Graceful Shutdown**: Configure `server.shutdown: graceful` and ensure SQS message listeners stop polling and await in-flight message completion upon receiving `SIGTERM`.
- **Event-Driven Autoscaling (KEDA)**:
  - Rather than scaling consumers on CPU/memory usage, deploy a **KEDA ScaledObject** triggered by SQS queue depth (`ApproximateNumberOfMessagesVisible`). When a traffic surge hits, consumer pods automatically scale out proportionally to drain the backlog.
- **Network Policies & Security Context**:
  - Run pods with non-root security contexts (`readOnlyRootFilesystem: true`, `runAsNonRoot: true`, `allowPrivilegeEscalation: false`).
  - Restrict inter-pod traffic using Kubernetes `NetworkPolicy`.

---

### 3. Missing Technical Requirements & Architectural Considerations

For real-world mission-critical deployment, the most prominent missing requirements and their architectural solutions include:

- **Multi-Stream & Multi-Entity Partitioning**:
  - *Current State*: The detector maintains a single global rolling window assuming all streaming points originate from a single sensor or metric source.
  - *Requirement*: Real-world streams ingest data from thousands of concurrent devices or metrics (e.g., `device_id`, `metric_name`).
  - *Solution*: Partition the baseline state by entity ID using a concurrent hash map or keyed state, or migrate to a stream-processing framework like **Apache Flink** or **Kafka Streams** where state is partitioned across keys natively.
- **Distributed State Beyond In-Process Memory**:
  - *Current State*: The `RollingWindow` lives entirely in JVM heap. If the consumer scales to $N$ pods, each pod observes only a fractional slice of the stream, leading to fragmented, inaccurate baselines. Furthermore, pod restarts lose the warmup history.
  - *Solution*:
    - For partitioned SQS consumers, route each entity to a consistent partition using SQS FIFO with `MessageGroupId` or Apache Kafka.
    - Alternatively, offload rolling baseline state to a low-latency shared state store (e.g., Redis cluster / Valkey) or compute rolling aggregates using streaming stateful workers.
- **Dedicated Anomaly Sink & Alert Routing**:
  - *Current State*: Detected anomalies are printed to stdout as log lines.
  - *Requirement*: Operational teams need automated alert dispatch, durable event history, and routing to downstream consumers.
  - *Solution*: Publish anomaly events to a dedicated `anomalies` SNS topic or Kafka topic, write them to a time-series database (TimescaleDB, InfluxDB), and route alerts to PagerDuty / Slack.
- **Robust & Non-Parametric Anomaly Detection**:
  - *Current State*: Z-score assumes a Gaussian (normal) distribution and is susceptible to extreme outliers skewing the mean and variance before they can be classified.
  - *Solution*: Support robust statistical baselines such as **Median Absolute Deviation (MAD)**, **Exponentially Weighted Moving Average (EWMA)**, or unsupervised algorithms like **Isolation Forests** to accommodate non-normal, skewed, or seasonal data.
- **Dynamic Configuration & Threshold Management**:
  - *Current State*: `windowSize` and `zThreshold` are static container environment variables requiring application restarts to change.
  - *Solution*: Integrate dynamic configuration management via **Spring Cloud Config**, **AWS AppConfig**, or **Consul**, enabling operators to adjust sensitivity thresholds per metric type at runtime without service interruptions.
