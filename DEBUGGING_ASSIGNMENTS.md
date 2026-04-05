# Debugging Assignments: Mini to Master 100

This repo is a good debugging gym because it already has:

- A React/Vite UI in `bench-ui`
- A Spring Boot backend in `benchmarking`
- Postgres via `docker-compose.yml`
- Flyway migrations
- Async workers, retries, timeouts, and circuit-breaker behavior
- Logs, metrics, and observability scaffolding

## How To Use This File

When you want a practice bug injected, say:

- `create bug A001`
- `create bug A042`
- `create bug A097`

I will then introduce that bug into this codebase and you can debug and fix it manually.

## Difficulty Map

- `A001-A020`: Mini fundamentals
- `A021-A040`: Core backend and API debugging
- `A041-A060`: Async, concurrency, and state transitions
- `A061-A080`: Data, infra, observability, and performance
- `A081-A100`: SDE 2-3 incident-grade drills

---

## A001-A020: Mini Fundamentals

1. `A001` | Mini | Target: `bench-ui/src/App.tsx` | Inject: change the runs history fetch from `/bench/runs` to a wrong path like `/bench/run` | Practice: browser network tracing, API contract mismatch.
2. `A002` | Mini | Target: `bench-ui/src/App.tsx` | Inject: submit a run with the wrong HTTP method | Practice: request inspection, controller mapping validation.
3. `A003` | Mini | Target: `bench-ui/src/App.tsx` | Inject: remove `cache: 'no-store'` from run history fetch | Practice: stale UI debugging, cache versus backend truth.
4. `A004` | Mini | Target: `bench-ui/src/App.tsx` | Inject: stop clearing `historyError` after a later successful fetch | Practice: stale state, false UI errors.
5. `A005` | Mini | Target: `bench-ui/src/App.tsx` | Inject: clear `selectedRunId` every time runs reload | Practice: state preservation across refreshes.
6. `A006` | Mini | Target: `bench-ui/src/App.tsx` | Inject: rename one terminal status string in the UI only, such as `PARTIALLY_COMPLETED` | Practice: enum drift between frontend and backend.
7. `A007` | Mini | Target: `bench-ui/src/App.tsx` | Inject: show `runningTasks + successTasks` as total in one summary card | Practice: spotting incorrect derived state.
8. `A008` | Mini | Target: `bench-ui/src/App.tsx` | Inject: stop calling `stopPolling()` on unmount | Practice: duplicate requests, memory leaks, runaway polling.
9. `A009` | Mini | Target: `bench-ui/src/App.tsx` | Inject: key expanded language sections by array index instead of language name | Practice: unstable UI state after data refresh.
10. `A010` | Mini | Target: `bench-ui/src/App.tsx` | Inject: build week-over-week chart URLs without encoding dataset or tool values | Practice: URL/path debugging.
11. `A011` | Mini | Target: `bench-ui/src/App.css` | Inject: make a modal or drawer `overflow: hidden` on mobile | Practice: visual debugging with DevTools.
12. `A012` | Mini | Target: `bench-ui/src/App.css` | Inject: introduce a `z-index` regression that blocks a primary button | Practice: layout stacking issues.
13. `A013` | Mini | Target: `benchmarking/src/main/java/com/devashish/learning/benchmarking/controllers/RootController.java` | Inject: rename a `@PathVariable` or endpoint segment incorrectly | Practice: 400/404 tracing from UI to controller.
14. `A014` | Mini | Target: `RootController.java` | Inject: return `200 OK` from run submission instead of `202 ACCEPTED` | Practice: API semantics, client assumptions.
15. `A015` | Mini | Target: `RootController.java` | Inject: remove `@Valid` from one request body | Practice: validation gaps and bad input reproduction.
16. `A016` | Mini | Target: `bench-ui/src/App.tsx` | Inject: send lowercase `status=running` where backend expects enum case | Practice: query parameter and enum parsing.
17. `A017` | Mini | Target: `bench-ui/src/App.tsx` | Inject: replace real error text with a generic message everywhere | Practice: preserving actionable error context.
18. `A018` | Mini | Target: `bench-ui/src/App.tsx` | Inject: hardcode page size to `5` in one path and `20` in another | Practice: inconsistent pagination behavior.
19. `A019` | Mini | Target: `bench-ui/src/App.tsx` | Inject: assume evaluation data is always present and remove one null guard | Practice: null safety and partial-response handling.
20. `A020` | Mini | Target: `bench-ui/src/App.tsx` or `bench-ui/src/main.tsx` | Inject: break one import or type name during a refactor | Practice: fast build-break triage.

## A021-A040: Core Backend and API Debugging

21. `A021` | Foundation | Target: run listing query/service | Inject: off-by-one pagination bug on `page` or `size` | Practice: API correctness, edge-case requests.
22. `A022` | Foundation | Target: run query service | Inject: sort runs oldest-first instead of newest-first | Practice: validating list ordering against user expectations.
23. `A023` | Foundation | Target: archive flow service/entity update | Inject: archive endpoint sets status but not `archived=true` | Practice: state-model consistency.
24. `A024` | Foundation | Target: resume flow | Inject: `failedOnly=true` still resumes successful tasks | Practice: filter correctness, business rule debugging.
25. `A025` | Foundation | Target: cancel flow | Inject: allow cancel on already terminal runs and return success anyway | Practice: invalid state transitions.
26. `A026` | Foundation | Target: run submission command service | Inject: ignore `Idempotency-Key` and create duplicate runs | Practice: request replay debugging.
27. `A027` | Foundation | Target: request/response DTO mapping | Inject: rename one JSON field on the backend only | Practice: contract drift, payload inspection.
28. `A028` | Foundation | Target: request DTO validation | Inject: weaken validation so empty language/tool values pass through | Practice: catching bad inputs early.
29. `A029` | Foundation | Target: `GlobalControllerAdvice.java` | Inject: map `ConflictException` to `500` instead of `409` | Practice: exception-to-HTTP mapping.
30. `A030` | Foundation | Target: `GlobalControllerAdvice.java` | Inject: leak raw exception messages or stack traces to clients | Practice: safe error surfacing.
31. `A031` | Foundation | Target: `benchmarking/src/main/resources/db/migration` | Inject: add a migration that breaks startup on a clean database | Practice: Flyway failure triage.
32. `A032` | Foundation | Target: `benchmarking/src/main/resources/application.yaml` | Inject: typo in `spring.datasource.url` or credentials key | Practice: app boot failure, config debugging.
33. `A033` | Foundation | Target: `docker-compose.yml` | Inject: break the Postgres healthcheck command or db name | Practice: container health versus service reachability.
34. `A034` | Foundation | Target: `application.yaml` or UI assumptions | Inject: mismatch backend port or base path used by the UI | Practice: local environment contract debugging.
35. `A035` | Foundation | Target: `RequestCorrelationFilter.java` | Inject: change the request-id header name in one direction only | Practice: request tracing and correlation loss.
36. `A036` | Foundation | Target: `logback-spring.xml` | Inject: remove `runId` or `taskId` from the pattern/MDC output | Practice: log usefulness and observability gaps.
37. `A037` | Foundation | Target: `application.yaml` | Inject: remove `health` or `metrics` from exposed actuator endpoints | Practice: environment diagnostics.
38. `A038` | Foundation | Target: `RootController.java` | Inject: return the chart endpoint with the wrong content type | Practice: browser/rendering mismatch debugging.
39. `A039` | Foundation | Target: `benchmarking/src/main/resources/configs/dataset-catalog.yaml` | Inject: typo a dataset key so one dataset disappears silently | Practice: config-driven behavior debugging.
40. `A040` | Foundation | Target: `benchmarking/src/main/resources/configs/execution-matrix.yaml` | Inject: remove or rename one tool-language combination | Practice: missing work generation and config audits.

## A041-A060: Async, Concurrency, and State Transitions

41. `A041` | Intermediate | Target: worker retry logic | Inject: off-by-one retry counter so a task gets one extra attempt | Practice: attempt lifecycle tracing.
42. `A042` | Intermediate | Target: `BenchmarkTaskWorkerService.java` | Inject: skip `concurrencyLimiter.release()` on one exception path | Practice: deadlock-like worker stalls.
43. `A043` | Intermediate | Target: `CommonThreadPoolConfig.java` or worker constructor | Inject: initialize concurrency from the wrong config value | Practice: throughput versus configuration debugging.
44. `A044` | Intermediate | Target: task claim path | Inject: claim fewer tasks than available capacity due to wrong `min/max` math | Practice: latent under-utilization.
45. `A045` | Intermediate | Target: `pickTasksFairly(...)` | Inject: make task picking greedy so one run starves others | Practice: fairness bugs under load.
46. `A046` | Intermediate | Target: stale-task recovery | Inject: make stale cutoff too aggressive and timeout healthy tasks | Practice: timing, clocks, and false-positive recovery.
47. `A047` | Intermediate | Target: stale-task recovery | Inject: update task status but forget to update the latest attempt entity | Practice: multi-entity consistency.
48. `A048` | Intermediate | Target: worker completion logic | Inject: catch a broad exception and replace the real root cause with a generic timeout/failure | Practice: preserving error provenance.
49. `A049` | Intermediate | Target: circuit-breaker map key | Inject: share one circuit breaker across all tools accidentally | Practice: blast-radius reasoning.
50. `A050` | Intermediate | Target: circuit-breaker reset logic | Inject: breaker opens but never closes after `openMs` | Practice: temporal logic debugging.
51. `A051` | Intermediate | Target: optimistic-lock retry wrapper | Inject: remove retry for claim or recovery operations | Practice: intermittent contention failures.
52. `A052` | Intermediate | Target: transaction boundaries in claim flow | Inject: perform claim/update/save outside one transaction | Practice: duplicate claims and race conditions.
53. `A053` | Intermediate | Target: run status recalculation | Inject: omit `SKIPPED_CIRCUIT_OPEN` from summary counters | Practice: aggregate correctness.
54. `A054` | Intermediate | Target: cancel versus complete paths | Inject: final run status becomes `COMPLETED` after a late cancel | Practice: racing terminal transitions.
55. `A055` | Intermediate | Target: resume and cancel flows | Inject: both operations can succeed concurrently on the same run | Practice: state-machine hardening.
56. `A056` | Intermediate | Target: timeout handling | Inject: interpret milliseconds as seconds or vice versa in one code path | Practice: unit mismatch debugging.
57. `A057` | Intermediate | Target: `taskWorkerExecutor` config | Inject: shrink queue capacity so bursts silently stall or reject work | Practice: thread-pool saturation analysis.
58. `A058` | Intermediate | Target: async worker execution | Inject: lose MDC context after `CompletableFuture.runAsync(...)` | Practice: cross-thread log correlation.
59. `A059` | Intermediate | Target: poll-and-recover ordering | Inject: recovery runs with bad timing and re-marks active tasks as stale | Practice: scheduler interaction bugs.
60. `A060` | Intermediate | Target: task attempt creation | Inject: start attempt numbering at `0` in one path and `1` in another | Practice: history integrity and auditability.

## A061-A080: Data, Infra, Observability, and Performance

61. `A061` | Advanced | Target: task query/repository path | Inject: introduce an N+1 fetch pattern when loading run tasks and attempts | Practice: SQL visibility and performance triage.
62. `A062` | Advanced | Target: Postgres schema/query design | Inject: remove or avoid an index needed by `/bench/runs` or task polling | Practice: slow query diagnosis.
63. `A063` | Advanced | Target: ready-task repository query | Inject: change ordering so one dataset/tool is always favored | Practice: queue bias debugging.
64. `A064` | Advanced | Target: list-runs filtering | Inject: archived runs appear in the active dashboard unexpectedly | Practice: repository filter validation.
65. `A065` | Advanced | Target: JPA entity versioning | Inject: remove optimistic-lock support from a hot entity | Practice: lost updates under concurrency.
66. `A066` | Advanced | Target: Flyway SQL | Inject: write a migration that works on your local state but fails on a clean environment | Practice: migration reproducibility.
67. `A067` | Advanced | Target: schema column definitions | Inject: shrink an error/result column so output gets truncated | Practice: silent data corruption detection.
68. `A068` | Advanced | Target: persistence or serialization of timestamps | Inject: store local time instead of UTC in one path | Practice: timezone and ordering bugs.
69. `A069` | Advanced | Target: `application.yaml` | Inject: switch `ddl-auto` from `validate` to `update` and hide schema drift locally | Practice: environment parity debugging.
70. `A070` | Advanced | Target: `logback-spring.xml` | Inject: send logs to a path that does not exist in one environment | Practice: “service is broken but logs are gone” debugging.
71. `A071` | Advanced | Target: `observability/` configs | Inject: label or scrape mismatch so one service’s logs never show in Grafana/Loki | Practice: pipeline-level observability debugging.
72. `A072` | Advanced | Target: health/metrics wiring | Inject: health endpoint stays `UP` while worker capacity is exhausted | Practice: health semantics versus real availability.
73. `A073` | Advanced | Target: metrics tags | Inject: add high-cardinality tags such as `runId` to a hot metric | Practice: telemetry design and cost awareness.
74. `A074` | Advanced | Target: result aggregation/UI rendering | Inject: fetch or render an oversized results payload that freezes the dashboard | Practice: backend payload size plus frontend rendering cost.
75. `A075` | Advanced | Target: chart/evaluation service | Inject: repeated database or aggregation work per chart render | Practice: CPU hotspot analysis.
76. `A076` | Advanced | Target: `toolExecutorPool()` | Inject: reduce tool executor capacity enough to create head-of-line blocking | Practice: executor versus worker interaction.
77. `A077` | Advanced | Target: DB pool/connection usage | Inject: connection leaks or excessive concurrent DB usage under multi-run load | Practice: pool exhaustion debugging.
78. `A078` | Advanced | Target: Docker/Postgres local setup | Inject: assumption that existing volume data is always present or always empty | Practice: cold-start environment issues.
79. `A079` | Advanced | Target: frontend import/path usage | Inject: case-sensitive import/path bug that works on one machine and fails on another | Practice: cross-platform reproducibility.
80. `A080` | Advanced | Target: test suite | Inject: fixed sleeps into async tests so they become flaky | Practice: test reliability engineering.

## A081-A100: SDE 2-3 Incident-Grade Drills

81. `A081` | Master | Target: submission/idempotency path | Inject: concurrent duplicate POSTs with the same `Idempotency-Key` create two runs | Practice: concurrency-safe deduplication.
82. `A082` | Master | Target: worker lifecycle | Inject: app crashes after claim and leaves tasks stuck in `RUNNING` until manual intervention | Practice: crash recovery reasoning.
83. `A083` | Master | Target: completion transaction paths | Inject: task status commits but run summary does not after a partial DB failure | Practice: atomicity and reconciliation.
84. `A084` | Master | Target: tool timeout/retry/circuit-breaker interactions | Inject: slow tool calls cause retry storms and breaker oscillation | Practice: resilience debugging under pressure.
85. `A085` | Master | Target: archive/resume behavior | Inject: archived runs can be resumed but stay hidden or partially visible in the UI | Practice: cross-layer state bugs.
86. `A086` | Master | Target: cancel plus stale recovery | Inject: cancel during recovery creates impossible counters like `pending > total` or both `CANCELLED` and `COMPLETED` effects | Practice: invariant-based debugging.
87. `A087` | Master | Target: config-driven planning | Inject: execution matrix and dataset catalog drift so tasks are created for nonexistent tool/dataset combos | Practice: root-cause across multiple config files.
88. `A088` | Master | Target: evaluation/baseline math | Inject: corrupted expected baseline data produces negative precision/recall or values above `1.0` | Practice: defensive analytics debugging.
89. `A089` | Master | Target: UI poller | Inject: dashboard keeps polling terminal runs forever after one edge-case status transition | Practice: lifecycle leaks and network noise.
90. `A090` | Master | Target: UI state management | Inject: historical results and evaluations accumulate in memory as users switch runs | Practice: memory leak diagnosis.
91. `A091` | Master | Target: prod logging profile | Inject: production JSON logs omit the exception payload needed for RCA | Practice: observing failures with degraded telemetry.
92. `A092` | Master | Target: request correlation plus async scheduling | Inject: request ID exists on ingress logs but disappears in async worker logs | Practice: distributed tracing without tracing tools.
93. `A093` | Master | Target: scheduler, queue, and executor interaction | Inject: one dataset/tool pair always times out only under mixed load because of starvation, not real slowness | Practice: misleading symptom analysis.
94. `A094` | Master | Target: DB migration and app compatibility | Inject: a schema change breaks rolling deployment between old and new code versions | Practice: backward compatibility.
95. `A095` | Master | Target: file logging and rotation | Inject: log rotation misconfiguration fills disk and degrades app performance | Practice: infra side effects on application behavior.
96. `A096` | Master | Target: run status/read APIs | Inject: one endpoint says a run is `COMPLETED` while another still shows pending tasks | Practice: read-model inconsistency and eventual consistency decisions.
97. `A097` | Master | Target: task claiming under concurrency | Inject: two workers can claim the same task after a lock/isolation change, producing duplicate attempts | Practice: deep race-condition debugging.
98. `A098` | Master | Target: servlet/controller/service interaction | Inject: slow chart rendering consumes request threads and makes health checks fail intermittently | Practice: cascading latency incidents.
99. `A099` | Master | Target: logging/observability config | Inject: the observability stack shows no errors while real failures happen because one logger path is filtered or unlabeled | Practice: debugging the monitoring system itself.
100. `A100` | Master | Target: full stack | Inject: combine wrong config, hidden retry storm, misleading metrics, and stale UI so the first visible symptom is false | Practice: SDE 2-3 incident leadership and structured debugging.

---

## Suggested Order

If you want the fastest growth path, do them in this order:

1. `A001-A020`
2. `A021-A040`
3. `A041-A060`
4. `A061-A080`
5. `A081-A100`

## What This Will Build

- Faster reproduction skills
- Stronger API-contract debugging
- Better thread, timeout, and race-condition reasoning
- Better DB and migration instincts
- Stronger observability habits
- Incident-style debugging judgment for SDE 2-3 level work
