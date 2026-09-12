alter table task add column process_instance_id uuid;
alter table task add column instance_step_id uuid;

create index task_process_instance_ix on task (process_instance_id) where process_instance_id is not null;

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_ASSIGN_STEP'),
    ('MANAGER', 'PROCESS_ASSIGN_STEP');

comment on column task.process_instance_id is
    'The run this task belongs to, or null. One of the two provenance facts PROCESS adds; nothing else.';
