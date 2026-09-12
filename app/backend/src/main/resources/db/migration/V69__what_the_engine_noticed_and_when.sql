create table automation_event (
    id uuid primary key,

    action varchar(40) not null,

    subject_kind varchar(20) not null,
    subject_id uuid not null,

    rung integer,
    occurred_at timestamptz not null,
    constraint automation_event_action check (
        action in (
            'ESCALATION_ADVANCED', 'STEP_STALL_DETECTED', 'LONG_BLOCK_DETECTED', 'STALE_REVIEW_DETECTED'
        )
    ),
    constraint automation_event_subject_kind check (subject_kind in ('TASK', 'STEP')),

    constraint automation_event_rung_belongs_to_an_escalation check (
        (action = 'ESCALATION_ADVANCED') = (rung is not null)
    )
);

create index automation_event_subject_ix on automation_event (subject_kind, subject_id, occurred_at desc);
create index automation_event_occurred_ix on automation_event (occurred_at desc);

comment on table automation_event is
    'What the unattended engine noticed and when. Append-only, no person, and read by nobody to decide '
    'anything -- every marker stays a function of current state (AUTOMATION_03 section 1).';
comment on column automation_event.rung is
    'Which rung of the ladder fired, for an escalation. The number, never who it reached: that is '
    'derivable from the task and the reporting tree, and storing it would build the table this feature '
    'refuses to have.';
