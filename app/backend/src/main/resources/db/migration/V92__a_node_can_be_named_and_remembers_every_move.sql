alter table work_node add column title varchar(120);

alter table work_node add column checklist jsonb;

create table work_node_state_transition (
    id            uuid primary key,
    node_id       uuid not null references work_node (id) on delete cascade,

    from_state    varchar(16),
    to_state      varchar(16) not null,

    actor_user_id uuid references auth_user (id),

    reason        text,

    occurred_at   timestamptz not null,

    seq           int not null default 0
);

create index work_node_state_transition_by_node on work_node_state_transition (node_id, occurred_at, seq);

comment on table work_node_state_transition is
    'Append-only. One row per move of work_node.state, written in the same transaction as the move '
    '(CONSTRAINT-EVENT-LOG-01) and NEVER updated or deleted. S3 Effort reads this and nothing else -- '
    'work_node.state overwrites in place and keeps only the last answer.';
