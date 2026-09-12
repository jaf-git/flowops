create table task_comment (
    id uuid primary key,
    task_id uuid not null references task (id),
    author_user_id uuid not null references auth_user (id),
    body text not null,
    created_at timestamptz not null
);

create index task_comment_task_ix on task_comment (task_id, created_at);

alter table task_state_transition
    add column overridden boolean not null default false;

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_REASSIGN'),
    ('MANAGER', 'TASK_REASSIGN');

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_OVERRIDE');
