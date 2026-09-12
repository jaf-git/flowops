create table chat_event (
    id              uuid primary key,
    conversation_id uuid        not null references conversation (id) on delete cascade,

    message_id      uuid        not null,
    action          varchar(50) not null,
    actor_user_id   uuid        not null references auth_user (id),
    occurred_at     timestamptz not null
);

alter table chat_event add constraint chat_event_action
    check (action in ('MESSAGE_SENT', 'MESSAGE_EDITED', 'MESSAGE_DELETED', 'MESSAGE_CONVERTED'));

create index chat_event_conversation_ix on chat_event (conversation_id, occurred_at desc);
create index chat_event_actor_ix on chat_event (actor_user_id, occurred_at desc);

comment on table chat_event is
    'CHAT_03 section 8. That a message existed, by whom and when -- never what it said. The absence of a body column is DECISION-CHAT-PRIVACY-01 held in the schema: an event log is reachable by more paths than a conversation, so a body here would be the administrative read CHAT_02 section 4 refuses.';
