alter table instance_step alter column definition_id drop not null;

alter table instance_step add column origin varchar(20) not null default 'DEFINITION';
alter table instance_step alter column origin drop default;

alter table instance_step add constraint instance_step_origin
    check (origin in ('DEFINITION', 'ATTACHED'));

alter table instance_step add constraint instance_step_definition_matches_origin
    check ((origin = 'DEFINITION') = (definition_id is not null));

drop index instance_step_task_ix;
create unique index instance_step_task_unique_ix on instance_step (task_id) where task_id is not null;

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_EDIT_INSTANCE'),
    ('MANAGER', 'PROCESS_EDIT_INSTANCE');

comment on column instance_step.origin is
    'DEFINITION for a step cut from a template, ATTACHED for a task put into the run afterwards.';
comment on column instance_step.title is
    'The planned title. Read only while the step has no task; a step with one shows the task''s own.';
