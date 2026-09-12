alter table process_instance add column archived_at timestamptz;

alter table process_instance add constraint process_instance_only_finished_runs_are_archived
    check (archived_at is null or state <> 'RUNNING');

create index process_instance_on_the_board_ix on process_instance (workspace_id)
    where archived_at is null;

comment on column process_instance.archived_at is
    'When somebody put this finished run away, or null while it is still on the operations board '
    '(PROCESS-ARCHIVE-INSTANCE-01). Reversible: setting it back to null returns the run to the board. '
    '**Read by the board and by nothing else** -- every figure the analysis and export surfaces compute '
    'counts an archived run exactly as it counted it before, because archiving is a statement about a '
    'screen and never about what happened.';

insert into auth_permission (name, delegable) values
    ('PROCESS_ARCHIVE_INSTANCE', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'PROCESS_ARCHIVE_INSTANCE'),
    ('MANAGER', 'PROCESS_ARCHIVE_INSTANCE');
