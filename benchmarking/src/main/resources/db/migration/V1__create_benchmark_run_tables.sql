create table benchmark_run (
    id uuid primary key,
    idempotency_key varchar(255) unique,
    requested_language varchar(100) not null,
    status varchar(50) not null,
    total_tasks integer not null,
    pending_tasks integer not null,
    running_tasks integer not null,
    success_tasks integer not null,
    failed_tasks integer not null,
    timeout_tasks integer not null,
    skipped_tasks integer not null,
    cancelled_tasks integer not null,
    submitted_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table benchmark_task (
    id uuid primary key,
    run_id uuid not null references benchmark_run(id) on delete cascade,
    language varchar(100) not null,
    dataset varchar(200) not null,
    tool varchar(100) not null,
    status varchar(50) not null,
    attempt_count integer not null,
    max_attempts integer not null,
    error_message text,
    raw_result text,
    next_retry_at timestamptz,
    last_failure_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_benchmark_task_status_retry on benchmark_task(status, next_retry_at, created_at);
create index idx_benchmark_task_run_id on benchmark_task(run_id);

create table task_attempt (
    id uuid primary key,
    task_id uuid not null references benchmark_task(id) on delete cascade,
    attempt_number integer not null,
    status varchar(50) not null,
    error_message text,
    duration_ms bigint,
    started_at timestamptz not null,
    completed_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (task_id, attempt_number)
);

create index idx_task_attempt_task_id on task_attempt(task_id);
