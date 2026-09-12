insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_REVIEW'),
    ('OWNER', 'TASK_CLOSE'),
    ('MANAGER', 'TASK_REVIEW'),
    ('MANAGER', 'TASK_CLOSE');

create table task_approval (
    id uuid primary key,

    task_id uuid not null unique references task (id),

    score smallint not null check (score between 1 and 5),

    comment text,
    reviewer_user_id uuid not null references auth_user (id),
    decided_at timestamptz not null
);

comment on table task_approval is
    'A judgement about one piece of work. Never aggregated per person: DECISION-APPROVAL-SCORE-01 holds by the absence of the query.';
