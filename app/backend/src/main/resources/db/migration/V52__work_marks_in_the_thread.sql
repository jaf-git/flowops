alter table message add column kind text not null default 'SPOKEN';
alter table message add column work_subject_kind text;
alter table message add column work_subject_id uuid;

alter table message alter column kind drop default;

alter table message add constraint message_kind
    check (kind in ('SPOKEN', 'WORK_MARK'));

alter table message alter column body drop not null;

alter table message add constraint message_body_matches_kind
    check ((kind = 'SPOKEN') = (body is not null));

alter table message drop constraint message_body_not_blank;
alter table message add constraint message_body_not_blank
    check (body is null or length(btrim(body)) > 0);

alter table message add constraint message_work_subject_matches_kind
    check ((kind = 'WORK_MARK') = (work_subject_kind is not null and work_subject_id is not null));

alter table message add constraint message_work_subject_kind
    check (work_subject_kind is null or work_subject_kind in ('TASK', 'RUN'));

alter table message add constraint message_mark_is_immutable_and_unconvertible
    check (kind = 'SPOKEN' or (edited_at is null and deleted_at is null and converted_task_id is null));

alter table chat_event drop constraint chat_event_action;
alter table chat_event add constraint chat_event_action
    check (action in ('MESSAGE_SENT', 'MESSAGE_EDITED', 'MESSAGE_DELETED', 'MESSAGE_CONVERTED', 'WORK_ASSIGNED'));

create index message_conversation_spoken_seq_ix
    on message (conversation_id, seq desc)
    where kind = 'SPOKEN';
