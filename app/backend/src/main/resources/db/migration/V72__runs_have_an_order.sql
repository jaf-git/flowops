alter table analysis_run add column seq bigserial;

create unique index analysis_run_by_seq on analysis_run (seq desc);

comment on column analysis_run.seq is
    'Insertion order. Used for "the latest run" because started_at is not unique - the demo clock is '
    'frozen, and two real runs can share a millisecond.';
