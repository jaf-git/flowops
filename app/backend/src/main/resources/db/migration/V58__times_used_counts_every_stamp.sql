update task_template t
set times_used = greatest(
        t.times_used,
        (select count(*) from task k where k.template_id = t.id));

comment on column task_template.times_used is
    'How many tasks have been created from this template, by every path: stamped by hand '
    '(TASKLIB-USE-TEMPLATE-01), raised on a recurrence, or cut from a process step. Incremented on use '
    'and never recomputed, so tidying task history cannot change it. Backfilled once in V58, when the '
    'third path began counting.';
