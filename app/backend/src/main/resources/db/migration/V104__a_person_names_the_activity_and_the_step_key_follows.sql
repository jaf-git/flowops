create table activity (
    id             uuid primary key,
    name           varchar(120) not null,
    slug           varchar(120) not null,

    status         varchar(8) not null default 'ACTIVE',
    merged_into_id uuid references activity (id),

    times_used     integer not null default 0,
    last_used_at   timestamptz,

    created_by     uuid not null references auth_user (id),
    created_at     timestamptz not null
);

create unique index activity_by_slug on activity (slug);
create index activity_by_use on activity (times_used desc, last_used_at desc);

alter table activity
    add constraint activity_status_is_known check (status in ('ACTIVE', 'MERGED', 'RETIRED'));

alter table activity
    add constraint activity_merged_names_its_survivor
        check ((status = 'MERGED') = (merged_into_id is not null));

alter table activity
    add constraint activity_slug_is_safe_in_a_step_key check (slug ~ '^[a-z0-9-]+$');

comment on table activity is
    'What a person says they did, chosen from a list rather than typed fresh. Workspace-wide and not '
    'department-owned, because "final review" is performed by Strategy on posts and by a team lead on '
    'articles, and two entities for one activity would re-fragment the processes (ACTIVITY_FIELD section 5).';

comment on column activity.slug is
    'The normalised name, and the identity. It is unique across ACTIVE, MERGED and RETIRED alike: a retired '
    'slug reissued would collapse two eras of history into one step. The charset is narrower than the name '
    'because the slug is carried inside a step key, where a colon forges an output type, a slash a '
    'conversation split, a hash a concept split and a plus a second step (ACTIVITY_FIELD section 3).';

comment on column activity.times_used is
    'Maintained on use, not counted live. It orders the suggestion list, where the top ten cover most marks.';

create table activity_usage (
    work_node_id      uuid primary key references work_node (id) on delete cascade,
    activity_id       uuid not null references activity (id),

    performer_role_id uuid references functional_role (id),
    counterparty_id   uuid references counterparty (id),

    used_by           uuid not null references auth_user (id),
    used_at           timestamptz not null
);

create index activity_usage_by_activity on activity_usage (activity_id, used_at desc);
create index activity_usage_by_role on activity_usage (activity_id, performer_role_id);
create index activity_usage_by_counterparty on activity_usage (activity_id, counterparty_id);

comment on table activity_usage is
    'One row per use, which is what makes a scoped suggestion possible: what I used recently, what my '
    'department uses, what this client''s work uses, then everything else. The primary key is the node '
    'because a mark carries at most one activity and correcting it replaces the row rather than adding one.';

comment on column activity_usage.used_by is
    'Who chose it, so a suggestion can say where it came from. No route aggregates a figure by this column '
    '- invariant 14, and the one this table would be the easiest place to break.';

alter table work_node
    add column activity_id uuid references activity (id);

create index work_node_by_activity on work_node (activity_id) where activity_id is not null;

comment on column work_node.activity_id is
    'Nullable, and that is the safety property rather than a compromise. A node without one keys its step on '
    'the work type exactly as every node did before this column existed, so a cold start still works and '
    'resolution improves as people name activities rather than requiring it first (ACTIVITY_FIELD section 3).';

alter table analysis_finding
    alter column subject_key type text;

comment on column analysis_finding.subject_key is
    'A process shape joined with plus signs. It outgrew varchar(200) the moment a step could be an activity '
    'rather than a work type: a thirteen-step shape is some five hundred characters, and the column would '
    'have refused the write and taken the whole analyser run down with it.';
