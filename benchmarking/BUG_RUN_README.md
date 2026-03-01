# Backend Bug Run (SDE-2 Debug Track)

This track is designed for iterative debugging practice on the backend.  
You will get one intentional bug at a time.

## How We Will Run This

1. I inject one bug (`Run N`).
2. You diagnose and fix it.
3. You ask for the next run.
4. I inject the next bug only after the current one is done.

## Bug Roadmap

### Run 1 (completed)
- Area: Request filtering logic
- Symptom: Requesting `language = "all"` unexpectedly returns empty/partial results.
- Skill focus: endpoint reproduction, logic tracing, condition debugging.

### Run 2 (active now)
- Area: Async execution wiring
- Symptom: API still works but runtime behavior suggests tasks are not truly parallel.
- Skill focus: Spring proxy/`@Async` behavior, thread-pool validation.

### Run 3
- Area: Exception-to-response mapping
- Symptom: malformed input returns wrong status code/payload shape.
- Skill focus: controller advice coverage and error taxonomy.

### Run 4
- Area: Data aggregation
- Symptom: one dataset/tool result overwrites another unexpectedly.
- Skill focus: map construction and key-collision debugging.

### Run 5
- Area: Config binding
- Symptom: YAML changes appear ignored at runtime.
- Skill focus: property binding structure and config import troubleshooting.

### Run 6
- Area: Validation edge case
- Symptom: a "valid-looking" payload fails validation unexpectedly.
- Skill focus: Bean Validation semantics on records and field types.

### Run 7
- Area: Async failure propagation
- Symptom: one failed tool execution causes surprising full-request behavior.
- Skill focus: `CompletableFuture` error propagation and aggregation strategy.

### Run 8
- Area: API contract consistency
- Symptom: response structure changes silently for a specific request path.
- Skill focus: contract validation and regression detection.

## Rules

- All bugs are backend-only.
- Bugs are realistic and compile unless the run specifically targets startup/compile failure.
- I will not provide exact line spoilers unless you ask for hints.

## Start

Run 2 is now injected in code.  
Compare timing/parallel behavior of tool execution for a request with multiple tools.
