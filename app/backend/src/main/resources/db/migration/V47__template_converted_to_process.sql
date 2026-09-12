alter table task_template
    add column converted_to_process_template_id uuid;

comment on column task_template.converted_to_process_template_id is
    'The process template this one became, when it turned out to describe work that changes hands '
    '(TASKLIB-CONVERT-TO-PROCESS-01). Null for everything that is genuinely one person''s work. '
    'Deliberately unconstrained: PROCESS owns the row it points at.';
