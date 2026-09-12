create table escalation_state (
    id uuid primary key,

    task_id uuid not null references task (id),

    rung integer not null,

    last_fired_at timestamptz not null,

    resolved_at timestamptz,
    constraint escalation_state_rung_in_range check (rung between 0 and 3),
    constraint escalation_state_resolution_is_not_in_the_past check (resolved_at is null or resolved_at >= last_fired_at)
);

create unique index escalation_state_one_open_episode_ix
    on escalation_state (task_id)
    where resolved_at is null;

create index escalation_state_live_ix on escalation_state (task_id) where resolved_at is null;

comment on table escalation_state is
    'The escalation ladder''s memory, and the only thing AUTOMATION stores. Keyed to a task and '
    'carrying no person: who was notified is derived from the assigner and the reporting tree at the '
    'moment of firing, never accumulated here (AUTOMATION_03 section 1).';
comment on column escalation_state.rung is
    'How far the ladder has climbed for this episode. 3 is terminal -- the ladder stops rather than '
    'reaching the owner for every stale task in the business.';
comment on column escalation_state.last_fired_at is
    'What the next interval is measured from, in working time, and the value the conditional update '
    'matches on so two overlapping evaluations produce one notification rather than two.';
comment on column escalation_state.resolved_at is
    'Set silently when the task leaves the overdue state. No notification announces a resolution; the '
    'absence of the marker is the message (UC-03 extension 5a).';
