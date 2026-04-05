alter table benchmark_run
    add column archived boolean not null default false,
    add column archived_at timestamptz;

create index idx_benchmark_run_archived_submitted_at on benchmark_run(archived, submitted_at desc);
