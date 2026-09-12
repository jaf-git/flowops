alter table instance_step add column task_template_id uuid;

alter table instance_step add constraint instance_step_template_needs_a_definition
    check (task_template_id is null or definition_id is not null);

create index instance_step_task_template_ix on instance_step (task_template_id)
    where task_template_id is not null;

comment on column instance_step.task_template_id is
    'The library entry this step''s work is, copied at instantiation (CONSTRAINT-TEMPLATE-FIRST-01). '
    'Null for a step attached from a free-form task. Never followed back to the template: a run is a '
    'snapshot, so this is what was agreed when it was cut rather than what the template says now.';
