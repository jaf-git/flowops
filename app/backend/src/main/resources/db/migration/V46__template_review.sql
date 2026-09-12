alter table task_template
    add column rejection_reason text;

comment on column task_template.rejection_reason is
    'Why this template was last sent back, so its author knows what to change '
    '(TASKLIB-APPROVE-TEMPLATE-01). Cleared when it is resubmitted; null when it has never been refused.';
