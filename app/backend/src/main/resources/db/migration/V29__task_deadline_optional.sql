alter table task
    alter column deadline drop not null;

alter table task
    add column deadline_set_by uuid references auth_user (id),
    add column deadline_set_at timestamptz,
    add column deadline_acknowledged_at timestamptz;

create index task_deadline_notice_ix
    on task (creator_user_id)
    where deadline_acknowledged_at is null and deadline_set_by is not null;
