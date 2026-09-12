create table department (
    id           uuid primary key,

    name         varchar(60) not null,

    display_order integer not null,
    created_at   timestamptz not null
);

create unique index department_name_is_unique on department (lower(name));

create table functional_role (
    id            uuid primary key,
    name          varchar(60) not null,

    department_id uuid not null references department (id),
    display_order integer not null,
    created_at    timestamptz not null
);

create unique index functional_role_name_is_unique on functional_role (lower(name));
create index functional_role_by_department on functional_role (department_id);

comment on table department is
    'What part of the business a person works in. Distinct from the reporting tree, which says who they '
    'answer to -- CHAT team channels follow the tree, not this.';
comment on table functional_role is
    'What a person actually does. The vocabulary an SOP prints under "Who", and the discriminator a '
    'role pair needs before it can tell two threads of work apart.';

alter table workspace_membership
    add column functional_role_id uuid references functional_role (id);

create index workspace_membership_by_functional_role on workspace_membership (functional_role_id);

comment on column workspace_membership.functional_role_id is
    'What this person does, and by derivation which department they are in. Null until somebody says; '
    'never a blocker on work.';

insert into department (id, name, display_order, created_at) values
    ('11111111-0000-4000-8000-000000000001', 'Client services', 1, now()),
    ('11111111-0000-4000-8000-000000000002', 'Content',         2, now()),
    ('11111111-0000-4000-8000-000000000003', 'Design',          3, now()),
    ('11111111-0000-4000-8000-000000000004', 'Ads',             4, now());

insert into functional_role (id, name, department_id, display_order, created_at) values
    ('22222222-0000-4000-8000-000000000001', 'Agency owner',    '11111111-0000-4000-8000-000000000001', 1, now()),
    ('22222222-0000-4000-8000-000000000002', 'Account manager', '11111111-0000-4000-8000-000000000001', 2, now()),
    ('22222222-0000-4000-8000-000000000003', 'Content writer',  '11111111-0000-4000-8000-000000000002', 3, now()),
    ('22222222-0000-4000-8000-000000000004', 'Editor',          '11111111-0000-4000-8000-000000000002', 4, now()),
    ('22222222-0000-4000-8000-000000000005', 'Designer',        '11111111-0000-4000-8000-000000000003', 5, now()),
    ('22222222-0000-4000-8000-000000000006', 'Ads specialist',  '11111111-0000-4000-8000-000000000004', 6, now());

alter table task_template drop constraint if exists task_template_responsible_role;
alter table task_template alter column responsible_role type varchar(60);

alter table process_template drop constraint if exists process_template_owner_role;
alter table process_template alter column owner_role type varchar(60);

comment on column task_template.responsible_role is
    'Who normally does this work, as a functional role and never as a person (SOP_02 section 10). Holds '
    'a functional_role name rather than a foreign key: an SOP is a document, and the words it printed '
    'should not change under it because somebody renamed a role afterwards.';
comment on column process_template.owner_role is
    'Which functional role normally owns a run of this process. Same shape and same reason as '
    'task_template.responsible_role.';

insert into auth_permission (name, delegable) values
    ('FUNCTIONAL_ROLE_ASSIGN', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'FUNCTIONAL_ROLE_ASSIGN'),
    ('MANAGER', 'FUNCTIONAL_ROLE_ASSIGN');
