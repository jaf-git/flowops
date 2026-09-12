alter table instance_step
    add column optional       boolean not null default false,
    add column condition_note text;

alter table instance_step
    add constraint instance_step_condition_needs_optional check (
        condition_note is null or optional
    );

alter table instance_step
    add column skipped_at timestamptz;

alter table instance_step
    add constraint instance_step_only_optional_is_skipped check (
        skipped_at is null or optional
    );

alter table instance_step drop constraint instance_step_assigned_has_a_task;
alter table instance_step
    add constraint instance_step_assigned_has_a_task check (
        case
            when skipped_at is not null then condition = 'CLOSED' and task_id is null
            else (condition in ('ASSIGNED', 'CLOSED')) = (task_id is not null)
        end
    );

alter table instance_step
    add constraint instance_step_skip_is_a_closure check (
        skipped_at is null or (task_id is null and closed_at is not null)
    );

create index instance_step_happened_ix on instance_step (instance_id) where skipped_at is null;

comment on column instance_step.skipped_at is
    'When the run owner answered that this optional step does not apply. The step is CLOSED so '
    'dependents release, but it is NOT a step that happened: every query computing what occurred in a '
    'run filters skipped_at is null (SOP_01 section 5). Null for every step that was actually done.';
