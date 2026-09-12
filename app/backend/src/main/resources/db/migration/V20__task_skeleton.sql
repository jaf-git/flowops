insert into auth_permission (name, delegable) values

    ('TASK_CREATE', false),
    ('TASK_VIEW_OWN', false),
    ('TASK_VIEW_SUBTREE', false),
    ('TASK_VIEW_ANY', false),
    ('TASK_ACT_OWN', false),
    ('TASK_EDIT', false),
    ('TASK_REASSIGN', false),
    ('TASK_OVERRIDE', false),

    ('TASK_DECIDE_DEADLINE', true),
    ('TASK_REVIEW', true),
    ('TASK_CLOSE', true),

    ('TASK_LIBRARY_MANAGE', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_CREATE'),
    ('OWNER', 'TASK_VIEW_OWN'),
    ('OWNER', 'TASK_VIEW_SUBTREE'),
    ('OWNER', 'TASK_VIEW_ANY'),
    ('OWNER', 'TASK_ACT_OWN'),

    ('MANAGER', 'TASK_CREATE'),
    ('MANAGER', 'TASK_VIEW_OWN'),
    ('MANAGER', 'TASK_VIEW_SUBTREE'),
    ('MANAGER', 'TASK_ACT_OWN'),

    ('EMPLOYEE', 'TASK_VIEW_OWN'),
    ('EMPLOYEE', 'TASK_ACT_OWN');

create table task (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    title varchar(200) not null,
    description text,

    assignee_user_id uuid not null references auth_user (id),
    creator_user_id uuid not null references auth_user (id),

    deadline timestamptz not null,
    priority varchar(20) not null,

    state varchar(20) not null,

    self_assigned boolean not null default false,
    at_risk boolean not null default false,
    created_at timestamptz not null,
    constraint task_title_not_blank check (length(btrim(title)) > 0)
);

create index task_assignee_ix on task (assignee_user_id, state);
create index task_creator_ix on task (creator_user_id, created_at desc);

create table task_phase_timer (
    id uuid primary key,
    task_id uuid not null references task (id),

    phase_kind varchar(20) not null,
    started_at timestamptz not null,

    ended_at timestamptz
);

create unique index task_phase_timer_one_open_ix
    on task_phase_timer (task_id)
    where ended_at is null;

create index task_phase_timer_task_ix on task_phase_timer (task_id, started_at);

create table task_state_transition (
    id uuid primary key,
    task_id uuid not null references task (id),

    from_state varchar(20),
    to_state varchar(20) not null,
    actor_user_id uuid not null references auth_user (id),

    reason text,
    occurred_at timestamptz not null
);

create index task_state_transition_task_ix on task_state_transition (task_id, occurred_at);

create table task_event (
    id uuid primary key,
    task_id uuid not null references task (id),
    action varchar(50) not null,
    actor_user_id uuid not null references auth_user (id),
    occurred_at timestamptz not null
);

create index task_event_task_ix on task_event (task_id, occurred_at desc);

comment on table task_phase_timer is
    'Discrete phase rows. Active time is the only interval attributed to the assignee; wait and blocked never are.';
