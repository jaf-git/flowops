alter table task
    add column stamped_estimated_hours numeric(6, 2);

alter table task
    add constraint task_estimate_snapshot_needs_a_template
        check (stamped_estimated_hours is null or template_id is not null);

comment on column task.stamped_estimated_hours is
    'What the template said this work would take, at the moment it was stamped (extension 2f). Null where '
    'nothing was stamped, nothing was said, or the task predates the column.';
