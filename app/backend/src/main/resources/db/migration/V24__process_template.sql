insert into auth_permission (name, delegable) values

    ('PROCESS_TEMPLATE_AUTHOR', false),
    ('PROCESS_TEMPLATE_EDIT', false),
    ('PROCESS_TEMPLATE_RETIRE', false),
    ('PROCESS_INSTANTIATE', false),

    ('PROCESS_VIEW_OWN', false),
    ('PROCESS_VIEW_SUBTREE', false),
    ('PROCESS_VIEW_ANY', false),
    ('PROCESS_ABANDON_INSTANCE', false),
    ('PROCESS_CHANGE_INSTANCE_OWNER', false),

    ('PROCESS_ASSIGN_STEP', true);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_TEMPLATE_AUTHOR'),
    ('OWNER', 'PROCESS_TEMPLATE_EDIT'),
    ('MANAGER', 'PROCESS_TEMPLATE_AUTHOR'),
    ('MANAGER', 'PROCESS_TEMPLATE_EDIT');

create table process_template (
    id uuid primary key,
    workspace_id uuid not null references workspace (id),
    name varchar(200) not null,
    overview text,
    author_user_id uuid not null references auth_user (id),

    active boolean not null default true,
    created_at timestamptz not null,
    constraint process_template_name_not_blank check (length(btrim(name)) > 0)
);

create unique index process_template_active_name_ix
    on process_template (workspace_id, lower(name))
    where active;

create index process_template_author_ix on process_template (author_user_id, created_at desc);

create table step_definition (
    id uuid primary key,
    template_id uuid not null references process_template (id),
    title varchar(200) not null,
    description text,

    expected_duration_hours integer,
    position integer not null,
    constraint step_definition_title_not_blank check (length(btrim(title)) > 0),
    constraint step_definition_duration_positive check (expected_duration_hours is null or expected_duration_hours > 0)
);

create index step_definition_template_ix on step_definition (template_id, position);

create table step_dependency (
    template_id uuid not null references process_template (id),
    dependent_step_id uuid not null references step_definition (id),
    depends_on_step_id uuid not null references step_definition (id),
    created_at timestamptz not null,
    primary key (template_id, dependent_step_id, depends_on_step_id),

    constraint step_dependency_not_self check (dependent_step_id <> depends_on_step_id)
);

create index step_dependency_depends_on_ix on step_dependency (depends_on_step_id);

create table process_event (
    id uuid primary key,
    template_id uuid references process_template (id),
    action varchar(50) not null,
    actor_user_id uuid not null references auth_user (id),
    occurred_at timestamptz not null
);

create index process_event_template_ix on process_event (template_id, occurred_at desc);

comment on table process_template is
    'How the business does a recurring thing. Copied into an instance at instantiation and never linked to it again.';
comment on table step_dependency is
    'Directed edges between steps of one template. A DAG, not a list: parallel steps are the reason.';
