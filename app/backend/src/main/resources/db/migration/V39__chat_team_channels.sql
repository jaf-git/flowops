alter table conversation add column team_manager_id uuid references auth_user (id);

alter table conversation drop constraint conversation_kind;
alter table conversation add constraint conversation_kind
    check (kind in ('DIRECT', 'CHANNEL', 'ANNOUNCEMENT'));

alter table conversation drop constraint conversation_pair_matches_kind;
alter table conversation add constraint conversation_pair_matches_kind
    check ((kind = 'DIRECT') = (participant_lo is not null and participant_hi is not null));

alter table conversation add constraint conversation_manager_matches_kind
    check ((kind = 'CHANNEL') = (team_manager_id is not null));

create unique index conversation_channel_manager_uk
    on conversation (workspace_id, team_manager_id)
    where kind = 'CHANNEL';

comment on column conversation.team_manager_id is
    'The manager whose team this channel belongs to. Membership is the manager plus their direct reports, read live from the reporting tree rather than chosen -- there is no route to create, rename, or add a member to a channel, because a hand-added member would be a person seeing team work the tree says they are not on (CHAT_UC_08).';
