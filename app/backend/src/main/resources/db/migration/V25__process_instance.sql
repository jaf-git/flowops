insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_INSTANTIATE'),
    ('OWNER', 'PROCESS_VIEW_OWN'),
    ('OWNER', 'PROCESS_VIEW_SUBTREE'),
    ('OWNER', 'PROCESS_VIEW_ANY'),
    ('MANAGER', 'PROCESS_INSTANTIATE'),
    ('MANAGER', 'PROCESS_VIEW_OWN'),
    ('MANAGER', 'PROCESS_VIEW_SUBTREE'),

    ('EMPLOYEE', 'PROCESS_VIEW_OWN');

create table process_instance (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    name varchar(200) not null,

    template_id uuid not null references process_template (id),

    process_owner_user_id uuid not null references auth_user (id),

    started_by_user_id uuid not null references auth_user (id),
    state varchar(20) not null,
    started_at timestamptz not null,
    completed_at timestamptz,
    constraint process_instance_name_not_blank check (length(btrim(name)) > 0),
    constraint process_instance_state check (state in ('RUNNING', 'COMPLETE'))
);

create index process_instance_owner_ix on process_instance (process_owner_user_id, started_at desc);
create index process_instance_template_ix on process_instance (template_id);

create table instance_step (
    id uuid primary key,
    instance_id uuid not null references process_instance (id),

    definition_id uuid not null,
    title varchar(200) not null,
    description text,
    expected_duration_hours integer,
    position integer not null,

    condition varchar(20) not null,

    task_id uuid,

    reachable_at timestamptz,
    closed_at timestamptz,
    constraint instance_step_title_not_blank check (length(btrim(title)) > 0),
    constraint instance_step_condition
        check (condition in ('PENDING', 'REACHABLE', 'ASSIGNED', 'CLOSED')),

    constraint instance_step_assigned_has_a_task
        check ((condition in ('ASSIGNED', 'CLOSED')) = (task_id is not null))
);

create index instance_step_instance_ix on instance_step (instance_id, position);
create index instance_step_task_ix on instance_step (task_id);

create index instance_step_condition_ix on instance_step (condition) where condition = 'REACHABLE';

create table instance_step_dependency (
    instance_id uuid not null references process_instance (id),
    dependent_step_id uuid not null references instance_step (id),
    depends_on_step_id uuid not null references instance_step (id),
    primary key (instance_id, dependent_step_id, depends_on_step_id),
    constraint instance_step_dependency_not_self check (dependent_step_id <> depends_on_step_id)
);

create index instance_step_dependency_depends_on_ix on instance_step_dependency (depends_on_step_id);

alter table process_event add column instance_id uuid references process_instance (id);

alter table process_event add constraint process_event_names_one_subject
    check ((template_id is not null) <> (instance_id is not null));

create index process_event_instance_ix on process_event (instance_id, occurred_at desc);

comment on table process_instance is
    'One run of a template, copied at instantiation and never linked to it again except as provenance.';
comment on column instance_step.reachable_at is
    'What the bottleneck is computed from. A step, never a person (DECISION-PROCESS-BOTTLENECK-01).';
