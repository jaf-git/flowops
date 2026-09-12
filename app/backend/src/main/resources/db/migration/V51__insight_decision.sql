create table insight_decision (
    id uuid primary key,

    kind varchar(40) not null,
    subject_type varchar(30) not null,
    subject_id uuid not null,

    finding_key text not null,
    decision varchar(10) not null,

    subject_fingerprint text not null,
    decider_user_id uuid not null references auth_user (id),
    decided_at timestamptz not null,
    constraint insight_decision_decision check (decision in ('APPLIED', 'DISMISSED'))
);

create index insight_decision_subject_ix on insight_decision (subject_type, subject_id, decided_at desc);

comment on table insight_decision is
    'What a person decided about a finding. Append-only, and this feature''s event log: no row is ever '
    'updated or deleted, and the newest row for a finding is its current state.';

alter table task_template
    add column approved_at timestamptz;

update task_template set approved_at = updated_at where status in ('APPROVED', 'RETIRED');

comment on column task_template.approved_at is
    'When the owner blessed this template. Null until approved. Backfilled from updated_at, which is '
    'exact for templates untouched since approval and late for those edited after it.';
