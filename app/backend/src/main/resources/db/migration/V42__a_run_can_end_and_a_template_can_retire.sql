alter table process_instance
    drop constraint process_instance_state;

alter table process_instance
    add constraint process_instance_state check (state in ('RUNNING', 'COMPLETE', 'ABANDONED'));

alter table process_instance
    add column abandoned_at timestamptz,
    add column abandoned_reason text;

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_TEMPLATE_RETIRE'),
    ('MANAGER', 'PROCESS_TEMPLATE_RETIRE'),
    ('OWNER', 'PROCESS_ABANDON_INSTANCE'),
    ('MANAGER', 'PROCESS_ABANDON_INSTANCE');

comment on column process_instance.abandoned_reason is
    'Why the run was stopped. Required (PROCESS-ABANDON-INSTANCE-01 criterion 4); free text, and the '
    'erasure sweep does not rewrite it.';
