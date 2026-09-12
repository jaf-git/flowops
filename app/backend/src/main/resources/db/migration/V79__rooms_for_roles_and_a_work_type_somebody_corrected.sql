alter table conversation drop constraint conversation_kind;

alter table conversation add constraint conversation_kind
    check (kind in ('DIRECT', 'GROUP', 'CHANNEL', 'ANNOUNCEMENT'));

alter table conversation add column name varchar(120);

update conversation
set kind = 'GROUP',
    name = coalesce(name, 'Team channel'),
    team_manager_id = null
where kind = 'CHANNEL';

alter table conversation add constraint conversation_name_matches_kind
    check ((kind in ('GROUP', 'CHANNEL')) = (name is not null));

alter table conversation add column functional_role_id uuid references functional_role (id);

alter table conversation drop constraint conversation_manager_matches_kind;

alter table conversation add constraint conversation_role_matches_kind
    check ((kind = 'CHANNEL') = (functional_role_id is not null));

create unique index conversation_channel_role_uk
    on conversation (workspace_id, functional_role_id)
    where kind = 'CHANNEL';

alter table work_bracket add column work_type_overridden boolean not null default false;

comment on column work_bracket.work_type_overridden is
    'R17.5 - somebody corrected the type derived from the performer''s role at mark time. Recorded rather '
    'than merely applied: a type nobody questioned and a type somebody fixed are different evidence, and '
    'an accumulation of overrides says the role-to-work mapping has drifted from how the business works.';
