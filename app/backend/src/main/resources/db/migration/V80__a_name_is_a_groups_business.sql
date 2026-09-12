alter table conversation drop constraint conversation_name_matches_kind;

alter table conversation add constraint conversation_name_matches_kind
    check ((kind = 'GROUP') = (name is not null) or kind = 'CHANNEL');

alter table conversation drop constraint conversation_role_matches_kind;

alter table conversation add constraint conversation_role_belongs_to_a_channel
    check (functional_role_id is null or kind = 'CHANNEL');
