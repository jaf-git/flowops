create table task_template (
    id uuid primary key,
    title text not null,
    description text,

    type text,

    priority varchar(10) not null,
    estimated_hours numeric(6, 2),

    checklist jsonb not null default '[]'::jsonb,

    status varchar(12) not null,

    author_id uuid not null references auth_user (id),

    times_used integer not null default 0,

    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint task_template_status check (status in ('DRAFT', 'PROPOSED', 'APPROVED', 'RETIRED')),
    constraint task_template_priority check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    constraint task_template_title_present check (length(trim(title)) > 0)
);

create index task_template_library_ix on task_template (times_used desc, created_at desc)
    where status = 'APPROVED';

create index task_template_pending_ix on task_template (created_at) where status = 'PROPOSED';

comment on column task_template.times_used is
    'How many tasks have been created from this template. Incremented on use (TASKLIB-USE-TEMPLATE-01); '
    'never recomputed, so tidying task history cannot change it.';

insert into auth_permission (name, delegable) values

    ('TASK_TEMPLATE_VIEW', true),
    ('TASK_TEMPLATE_CREATE', true),

    ('TASK_TEMPLATE_APPROVE', false),
    ('TASK_TEMPLATE_RETIRE', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_TEMPLATE_VIEW'),
    ('MANAGER', 'TASK_TEMPLATE_VIEW'),
    ('EMPLOYEE', 'TASK_TEMPLATE_VIEW'),
    ('OWNER', 'TASK_TEMPLATE_CREATE'),
    ('MANAGER', 'TASK_TEMPLATE_CREATE'),
    ('EMPLOYEE', 'TASK_TEMPLATE_CREATE'),
    ('OWNER', 'TASK_TEMPLATE_APPROVE'),
    ('MANAGER', 'TASK_TEMPLATE_APPROVE'),
    ('OWNER', 'TASK_TEMPLATE_RETIRE'),
    ('MANAGER', 'TASK_TEMPLATE_RETIRE');
