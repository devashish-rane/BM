# BM On-Call KT Runbook

## Purpose

This document is the operator handoff for BM.

Use it when you are:

- on call for the BM package
- debugging a broken run
- trying to understand how a request moves through the system
- checking whether a symptom is a real incident or expected behavior

This is written for fast operational understanding, not as a design pitch.

## What BM Is

BM is a benchmarking system with:

- a Spring Boot backend in `benchmarking`
- a React/Vite UI in `bench-ui`
- a Postgres database from `docker-compose.yml`

At a high level, BM:

- accepts a benchmark run request
- expands that request into many tasks using config
- stores run and task state in Postgres
- executes tasks asynchronously in the background
- exposes status, results, evaluations, and charts over HTTP

The intended primary flow is:

`submit -> persist -> worker claims tasks -> async tool execution -> query/poll for status`

## Current Reality You Should Know Up Front

These points matter on call because they affect what is and is not a real production issue.

- The tool adapters are currently simulated, not real external tool integrations.
- `semgrep` and `qca` sleep for about 2 seconds and then return dummy JSON from `src/main/resources/dummy-data`.
- `llm` returns dummy JSON immediately.
- Evaluation numbers are also simulated. Precision, recall, and F1 are derived heuristically from stored result size and dataset metadata.
- There are two execution paths in the backend.
- `/bench/runs` is the proper persisted async run model and is the path you should reason about first.
- `/bench/weekly-trigger` is a legacy synchronous path that still executes all tasks and waits for completion in-request.
- A long-running run can be healthy even if the client has no immediate results. Status polling is part of the design.

## Main Components

### Backend

Core backend areas:

- `controllers/RootController.java`
- `serivces/BenchmarkRunCommandService.java`
- `serivces/BenchmarkRunQueryService.java`
- `serivces/BenchmarkTaskWorkerService.java`
- `serivces/RunOrchrestratorService.java`
- `serivces/ToolExecutionGateway.java`
- `serivces/evaluation/*`

### Persistence

Main tables:

- `benchmark_run`
- `benchmark_task`
- `task_attempt`

Important behavior:

- one run contains many tasks
- one task can have many attempts
- run counters are denormalized on the run row for fast status reads

### Configuration

Main config sources:

- `benchmarking/src/main/resources/application.yaml`
- `benchmarking/src/main/resources/configs/execution-matrix.yaml`
- `benchmarking/src/main/resources/configs/dataset-catalog.yaml`

What each one does:

- `application.yaml` controls runtime behavior such as concurrency, retries, timeout, and DB connection
- `execution-matrix.yaml` decides which `language x dataset x tool` combinations become tasks
- `dataset-catalog.yaml` provides source repo metadata and expected findings used by evaluation

## Core Domain Model

### Run

A run is the top-level unit submitted by a client.

It stores:

- run id
- requested language
- overall status
- archived flag
- submitted, started, completed, archived timestamps
- total and per-status task counters

Run statuses:

- `QUEUED`
- `RUNNING`
- `COMPLETED`
- `PARTIALLY_COMPLETED`
- `FAILED`
- `CANCELLED`

How to interpret them:

- `QUEUED`: tasks exist but none are running yet
- `RUNNING`: at least one task is running
- `COMPLETED`: all tasks succeeded
- `PARTIALLY_COMPLETED`: at least one task succeeded and at least one task did not
- `FAILED`: all tasks ended in non-success terminal states
- `CANCELLED`: the run was cancelled

### Task

A task is one concrete `language + dataset + tool` execution item.

Task statuses:

- `PENDING`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `TIMEOUT`
- `SKIPPED_CIRCUIT_OPEN`
- `CANCELLED`

Important fields:

- `attemptCount`
- `maxAttempts`
- `errorMessage`
- `rawResult`
- `nextRetryAt`
- `startedAt`
- `completedAt`

### Attempt

An attempt is the audit record for one execution try of a task.

It stores:

- attempt number
- status
- error message
- duration
- started/completed timestamps

## End-to-End Request Flow

### Recommended Async Path: `POST /bench/runs`

1. Client submits `runid` and `language`.
2. `RootController` forwards to `BenchmarkRunCommandService`.
3. The command service validates the language and checks `Idempotency-Key` if present.
4. `BenchmarkTaskPlanService` reads the execution matrix and creates one task per `language x dataset x tool`.
5. A `benchmark_run` row is created with counters initialized.
6. All `benchmark_task` rows are inserted with status `PENDING`.
7. API returns `202 ACCEPTED` with run metadata.
8. The scheduled worker polls every second.
9. The worker claims ready `PENDING` tasks with `FOR UPDATE SKIP LOCKED`.
10. Claimed tasks are moved to `RUNNING` and a `task_attempt` row is created.
11. The worker dispatches each task through `RunOrchrestratorService`.
12. The orchestrator calls `ToolExecutionGateway`.
13. The gateway picks the tool bean by name such as `semgrep`, `qca`, or `llm`.
14. On success, the task becomes `SUCCESS` and `rawResult` is stored.
15. On failure or timeout, the worker either re-queues the task or marks it terminal.
16. The run counters are updated after each terminal transition.
17. Clients poll `/bench/runs/{runId}` and related read endpoints until the run is terminal.

### Legacy Synchronous Path: `POST /bench/weekly-trigger`

This path still exists and behaves differently:

- it plans tasks directly
- it fires futures for all tasks in the request thread path
- it waits for all of them to finish before returning
- it does not use the persisted run/task lifecycle

Operational guidance:

- do not treat `/bench/weekly-trigger` timing as representative of the async worker path
- if a user reports BM is "not async", first confirm which endpoint they are using

## Task Planning Logic

Planning is config-driven.

BM reads `execution-matrix.yaml` like:

- language
- dataset
- list of tools for that dataset

Example meaning:

- `java -> owasp -> [semgrep, llm, qca]` becomes 3 tasks
- `javascript -> owasp_js -> [semgrep, llm]` becomes 2 tasks

Important implication:

- one dataset does not necessarily mean one task
- runtime and queue size depend on total planned tasks, not just dataset count

## Worker, Retry, Timeout, and Circuit Breaker Behavior

### Worker Polling

The worker:

- runs on a fixed delay
- recovers stale `RUNNING` tasks before claiming more work
- calculates available capacity from a semaphore
- claims up to the smaller of available capacity and worker batch size
- spreads claimed work fairly across runs

### Claiming Behavior

Ready tasks are:

- `PENDING`
- `next_retry_at` is null or already due

Claiming uses `FOR UPDATE SKIP LOCKED`, which helps prevent duplicate claims under concurrent polling.

### Retry Behavior

Current config means:

- `max-retries: 1`
- practical max attempts per task is `2`

How retries work:

- task starts as `PENDING`
- worker marks it `RUNNING`
- if execution fails and attempts remain, task returns to `PENDING`
- `next_retry_at` is set using `retry-backoff-ms`
- when attempts are exhausted, task becomes terminal

### Timeout Behavior

Current timeout:

- `task-timeout-ms: 600000`
- this is 10 minutes

What happens on timeout:

- the execution future is cancelled
- task is marked `TIMEOUT`
- if retries remain, it is re-queued
- otherwise it becomes terminal

### Stale Task Recovery

Recovery exists for crash-like or wedged situations.

A `RUNNING` task can be recovered as stale when:

- it is still `RUNNING`
- `started_at` is older than `task-timeout-ms + stale-running-task-grace-ms`

Current extra grace:

- `stale-running-task-grace-ms: 30000`
- this is 30 seconds

### Circuit Breaker

The worker keeps a circuit breaker per tool name.

Current behavior:

- breaker opens after `3` consecutive failures for a tool
- breaker stays open for `60000 ms`
- tasks hitting an open breaker are marked `SKIPPED_CIRCUIT_OPEN`

Operational meaning:

- many `SKIPPED_CIRCUIT_OPEN` tasks usually indicate repeated failure inside one tool adapter
- this is tool-scoped, not run-scoped

## Thread Pools and Concurrency

There are two executors:

- `taskWorkerExecutor`
- `toolExecutorPool`

Current config and behavior:

- `max-concurrency: 6`
- worker concurrency is bounded to 6 via semaphore and executor sizing
- `toolExecutorPool` is configured with core size 4 and max size 8

What this means in practice:

- at most 6 benchmark tasks should be actively driven by the worker at once
- task throughput depends on task duration, timeout, and downstream tool behavior

## HTTP API Quick Reference

### Submission and Lifecycle

- `POST /bench/runs`
- `GET /bench/runs/{runId}`
- `GET /bench/runs/{runId}/tasks`
- `GET /bench/runs/{runId}/results`
- `POST /bench/runs/{runId}/cancel`
- `POST /bench/runs/{runId}/resume`
- `POST /bench/runs/{runId}/archive`
- `GET /bench/runs`

### Evaluation and Charts

- `GET /bench/runs/{runId}/evaluations`
- `GET /bench/analytics/overview`
- `GET /bench/charts/week-over-week`
- `GET /bench/charts/week-over-week/{runId}/{language}/{dataset}/{tool}`

### Legacy

- `POST /bench/weekly-trigger`

### Common Response Semantics

- submit uses `202 ACCEPTED`
- read endpoints use `200 OK`
- validation and bad input map to `400`
- missing run or task data maps to `404`
- invalid state transitions map to `409`

## Local Operations

### Start Postgres

```bash
cd BM
docker compose up -d postgres
```

### Run Backend

```bash
cd BM/benchmarking
./mvnw spring-boot:run
```

Backend defaults:

- port `8081`
- DB `jdbc:postgresql://localhost:5432/benchmarking`
- DB user `benchmarking`
- DB password `benchmarking`

### Run UI

```bash
cd BM/bench-ui
npm install
npm run dev
```

### Package Backend

```bash
cd BM/benchmarking
./mvnw -q -DskipTests package
```

### API Smoke Test

```bash
curl -X POST http://localhost:8081/bench/runs \
  -H 'Content-Type: application/json' \
  -d '{"runid":"123e4567-e89b-12d3-a456-426614174111","language":"all"}'
```

```bash
curl http://localhost:8081/bench/runs/123e4567-e89b-12d3-a456-426614174111
```

There is also a basic request file at `api-testing/root.http`.

## Observability and What to Look At First

### Logs

Request correlation:

- ingress requests use `X-Request-Id`
- request id is written to MDC by `RequestCorrelationFilter`

Worker correlation:

- worker adds `runId`
- worker adds `taskId`
- worker adds `tool`

These MDC values are the fastest way to trace one failing run across async execution.

Local and non-prod logging behavior:

- logs are written to console
- logs are also written to `logs/benchmarking.log`

### Metrics

The worker records:

- `benchmark.task.completed`
- `benchmark.task.duration`
- `benchmark.task.retry`
- `benchmark.task.recovered_stale_running`

Actuator exposure currently includes:

- `health`
- `info`
- `metrics`

Useful actuator URLs:

- `http://localhost:8081/actuator/health`
- `http://localhost:8081/actuator/info`
- `http://localhost:8081/actuator/metrics`

## What Good Looks Like

A healthy async run usually looks like this:

- submit returns `202`
- run appears as `QUEUED`
- within one or two poll cycles, some tasks move to `RUNNING`
- task counters move steadily from pending to terminal states
- `SUCCESS` tasks accumulate results
- run becomes `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, or `CANCELLED`

## First 15 Minutes of Incident Triage

When BM looks broken, do this in order:

1. Confirm which endpoint path is involved.
2. Check if the backend process is up and the DB is reachable.
3. Fetch run status from `/bench/runs/{runId}`.
4. Fetch task list from `/bench/runs/{runId}/tasks`.
5. Check logs for `runId`, `taskId`, `tool`, and `X-Request-Id`.
6. Compare task counts on the run with actual task rows if needed.
7. Check whether tasks are stuck in `PENDING`, `RUNNING`, or terminal failure states.
8. Check whether the execution matrix created the expected number of tasks.
9. Check timeout, retry, and circuit-breaker config before assuming a tool outage.

## Symptom-to-Diagnosis Guide

### Symptom: Run stays `QUEUED`

Likely causes:

- worker is not running
- no executor capacity is available
- tasks are waiting for `next_retry_at`
- scheduler is alive but claim query is not finding ready tasks

Checks:

- verify backend is up
- inspect task rows for `status = PENDING`
- check `next_retry_at`
- check logs for "Claimed X benchmark task(s) for execution"
- confirm `max-concurrency` is not effectively zero

### Symptom: Run stays `RUNNING` too long

Likely causes:

- slow tool execution
- timeout too high for the observed failure mode
- worker completed execution but failed final persistence due to contention
- process crashed after claim

Checks:

- inspect `started_at` on running tasks
- compare age against `task-timeout-ms + stale-running-task-grace-ms`
- inspect `task_attempt` for latest status and duration
- look for optimistic-lock warnings in logs

### Symptom: Many `TIMEOUT` tasks

Likely causes:

- actual tool slowness
- timeout set too low for the workload
- executor starvation makes healthy work appear slow

Checks:

- confirm task timeout config
- compare task count and concurrency to expected throughput
- inspect whether failures are clustered by tool

### Symptom: Many `SKIPPED_CIRCUIT_OPEN` tasks

Likely causes:

- repeated tool-specific failures opened the breaker
- tool name in planning maps to a broken adapter

Checks:

- inspect earlier failures for the same tool
- confirm the tool bean exists
- confirm the adapter can load its dummy resource file

### Symptom: Submit succeeds but results are empty

Likely causes:

- tasks have not reached `SUCCESS` yet
- tasks ended in non-success states
- looking at the legacy path versus async path incorrectly

Checks:

- inspect `/tasks`
- inspect run counters
- remember `/results` only returns successful tasks with non-null `rawResult`

### Symptom: Evaluation numbers look wrong

Important reality:

- evaluation values are simulated, not authoritative scanner metrics

Checks:

- inspect `dataset-catalog.yaml`
- inspect task `rawResult`
- remember detection count is based on JSON node count or line count fallback

### Symptom: Wrong number of tasks created

Likely causes:

- misunderstanding of dataset-to-tool fanout
- drift in `execution-matrix.yaml`
- unsupported language request

Checks:

- inspect the requested language
- count configured `dataset x tool` combinations in the matrix
- remember `language = all` expands across all configured languages

## Useful SQL During Incidents

Count run states:

```sql
select id, requested_language, status, total_tasks, pending_tasks, running_tasks,
       success_tasks, failed_tasks, timeout_tasks, skipped_tasks, cancelled_tasks,
       submitted_at, started_at, completed_at
from benchmark_run
order by submitted_at desc
limit 20;
```

Inspect tasks for one run:

```sql
select id, language, dataset, tool, status, attempt_count, max_attempts,
       next_retry_at, started_at, completed_at, error_message
from benchmark_task
where run_id = '<run-id>'
order by language, dataset, tool;
```

Inspect attempt history for one task:

```sql
select task_id, attempt_number, status, duration_ms, started_at, completed_at, error_message
from task_attempt
where task_id = '<task-id>'
order by attempt_number;
```

Find stale running tasks:

```sql
select id, run_id, language, dataset, tool, started_at
from benchmark_task
where status = 'RUNNING'
order by started_at asc;
```

## Operational Actions You Can Safely Take

Safe actions:

- query status, tasks, results, and evaluations
- cancel a run
- resume failed or timed-out tasks
- archive a terminal run
- restart the backend if tasks appear stuck and stale recovery is needed after restart

Use caution with these:

- manually editing DB rows
- changing execution matrix during an active incident without understanding fanout impact
- using `/bench/weekly-trigger` to validate async worker health

## Behavior of Cancel, Resume, and Archive

### Cancel

Cancel behavior:

- only pending tasks are marked `CANCELLED` immediately
- already running tasks are not force-killed by the cancel endpoint
- run status becomes `CANCELLED`

This means a cancelled run can still have tasks that were already in flight finishing around the same time.

### Resume

Resume behavior:

- only terminal tasks are eligible
- default behavior is effectively `failedOnly = true`
- resume can be filtered by languages, datasets, and tools
- resumed tasks return to `PENDING`
- `maxAttempts` is extended for resumed tasks

### Archive

Archive behavior:

- only terminal runs can be archived
- archive does not delete data
- archived state is tracked on the run row

## Known Caveats and Risks

- Tool adapters are dummy implementations today, so throughput and failure semantics are not real-world scanner behavior.
- `/bench/weekly-trigger` does not match the main async/persisted design and can confuse debugging if treated as the primary path.
- Evaluation output is simulated and should not be used as hard truth for scanner quality.
- The code relies on denormalized counters in `benchmark_run`, so if counts ever look impossible, compare them with `benchmark_task` rows and use recalculation logic as the source of truth.
- Optimistic-lock contention is handled defensively, but heavy concurrent updates can still produce temporary confusing states until the next poll or stale recovery cycle.

## Fast Mental Model

If you remember only one thing, remember this:

- BM is a job queue backed by Postgres
- run submission creates queued work
- a scheduled worker claims tasks under bounded concurrency
- each task calls a named tool adapter
- status APIs read the stored state, not live in-memory progress

## Suggested KT Walkthrough for a New On-Call Engineer

If you are transferring knowledge live, walk through BM in this order:

1. Show `RootController` and the main `/bench/runs` endpoints.
2. Show `BenchmarkTaskPlanService` and explain config-driven task fanout.
3. Show `BenchmarkRunCommandService` for submission, cancel, resume, and archive behavior.
4. Show `BenchmarkTaskWorkerService` for claiming, retries, timeouts, stale recovery, and circuit breaker logic.
5. Show `ToolExecutionGateway` and the dummy tool adapters.
6. Show `BenchmarkRunQueryService` and `/results`.
7. Show `BenchmarkEvaluationService` and explain that scores are simulated.
8. Run one local submission and trace it through DB rows and logs.

## Source File Map

If you need to go from incident symptom to source quickly, start here:

- request entry: `benchmarking/src/main/java/com/devashish/learning/benchmarking/controllers/RootController.java`
- command flow: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/BenchmarkRunCommandService.java`
- query flow: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/BenchmarkRunQueryService.java`
- task planning: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/BenchmarkTaskPlanService.java`
- worker loop: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/BenchmarkTaskWorkerService.java`
- async dispatch: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/RunOrchrestratorService.java`
- tool lookup: `benchmarking/src/main/java/com/devashish/learning/benchmarking/serivces/ToolExecutionGateway.java`
- error mapping: `benchmarking/src/main/java/com/devashish/learning/benchmarking/advice/GlobalControllerAdvice.java`
- runtime config: `benchmarking/src/main/resources/application.yaml`
- plan config: `benchmarking/src/main/resources/configs/execution-matrix.yaml`
- dataset metadata: `benchmarking/src/main/resources/configs/dataset-catalog.yaml`
- DB schema: `benchmarking/src/main/resources/db/migration/V1__create_benchmark_run_tables.sql`
