create table bracket_join_intent (
    id                uuid primary key,

    job_id            uuid        not null references job (id),

    joiner_bracket_id uuid        not null references work_bracket (id),
    joined_bracket_id uuid        not null references work_bracket (id),

    declared_by       uuid        not null references auth_user (id),
    declared_at       timestamptz not null,

    constraint bracket_join_intent_is_between_two_brackets
        check (joiner_bracket_id <> joined_bracket_id)
);

create unique index bracket_join_intent_once
    on bracket_join_intent (least(joiner_bracket_id, joined_bracket_id),
                            greatest(joiner_bracket_id, joined_bracket_id));

create index bracket_join_intent_by_job on bracket_join_intent (job_id);

comment on table bracket_join_intent is
    'R21.7 - an explicit join, stored because it cannot be derived. Ranks above R19.1''s shared-output '
    'tier: the derived group came back empty for a real collaboration, and the button did not.';
