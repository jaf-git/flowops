alter table process_instance alter column template_id drop not null;

comment on column process_instance.template_id is
    'The template this run was cut from, or null for a run started from tasks '
    '(PROCESS-START-FROM-TASKS-01). Provenance only: nothing reads it to decide behaviour.';
