create sequence canvas_event_seq;

alter table task_event add column seq bigint;

update task_event event
set seq = ordered.position
from (
    select id, row_number() over (order by occurred_at, id) as position
    from task_event
) ordered
where event.id = ordered.id;

select setval('canvas_event_seq', coalesce((select max(seq) from task_event), 0) + 1, false);

alter table task_event alter column seq set default nextval('canvas_event_seq');
alter table task_event alter column seq set not null;

create unique index task_event_seq_ix on task_event (seq);

comment on column task_event.seq is
    'The stream cursor (DECISION-REALTIME-PROTOCOL-01). Monotonic, gapless enough to order by, and never reused. Written by the default, never by the application.';
