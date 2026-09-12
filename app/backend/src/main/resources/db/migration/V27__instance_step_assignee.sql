alter table instance_step add column assignee_user_id uuid references auth_user (id);

create index instance_step_assignee_ix on instance_step (assignee_user_id)
    where assignee_user_id is not null;

comment on column instance_step.assignee_user_id is
    'Who the Process Owner gave this step to. An identifier; the name is resolved at read time and never stored.';
