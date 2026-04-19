# BM Request Flow (Abstract)

## Core Idea

The system follows an **accept → persist → process asynchronously → poll for status/results** pattern.

## Abstract Flow

```text
UI / API Client
  -> /bench endpoints
  -> Request filter adds correlation id
  -> Controller selects command/query/evaluation path
  -> Service layer validates intent and coordinates work
  -> Repository layer persists or reads run/task state

For submit requests:
  -> A benchmark run is created
  -> Individual benchmark tasks are generated from config
  -> API returns quickly with run metadata

Background execution:
  -> Scheduled worker polls pending tasks
  -> Tasks are claimed under concurrency limits
  -> Async orchestrator dispatches each task to the right tool
  -> Tool execution returns success, timeout, or failure
  -> Task attempt + run summary state are updated in storage

For read requests:
  -> Query services read persisted run/task data
  -> Responses are shaped into status, results, tasks, or evaluations
  -> UI keeps polling until the run reaches a terminal state

Cross-cutting:
  -> Controller advice converts validation/business failures into HTTP errors
  -> Correlation id flows through request handling for traceability
```

## Simple Request Picture

```mermaid
flowchart LR
  A[UI or API Client] --> B[/bench endpoint]
  B --> C[Correlation Filter]
  C --> D[Root Controller]
  D --> E[Command / Query / Evaluation Service]
  E --> F[(Run and Task Data)]
  F --> G[Scheduled Worker]
  G --> H[Async Orchestrator]
  H --> I[Tool Gateway / Tool Adapter]
  I --> F
  F --> J[Query / Evaluation Service]
  J --> K[Status / Results / Analytics Response]
```

## One-Line Summary

BM accepts benchmark requests synchronously, stores them as runs/tasks, executes work asynchronously in the background, and serves progress/results through polling-friendly query endpoints.
