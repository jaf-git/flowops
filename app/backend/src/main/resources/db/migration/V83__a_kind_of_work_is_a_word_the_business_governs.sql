create table work_type_vocabulary (
    code          varchar(40) primary key,

    label         varchar(80) not null,

    active        boolean not null default true,

    display_order integer not null,
    created_at    timestamptz not null,

    constraint work_type_vocabulary_label_not_blank check (length(btrim(label)) > 0)
);

create index work_type_vocabulary_active on work_type_vocabulary (display_order) where active;

comment on table work_type_vocabulary is
    'The kinds of work this business does. Seeded from WorkTypeCatalogue, which keeps the rule that maps '
    'a role to a type; this table holds the set of types that rule may produce.';

insert into work_type_vocabulary (code, label, active, display_order, created_at) values
    ('CLIENT_INTAKE', 'Client intake', true,  1,  now()),
    ('COORDINATION',  'Coordination',  true,  2,  now()),
    ('CONTENT',       'Content',       true,  3,  now()),
    ('DESIGN',        'Design',        true,  4,  now()),
    ('PHOTO',         'Photography',   true,  5,  now()),
    ('VIDEO',         'Video',         true,  6,  now()),
    ('ADS',           'Ads',           true,  7,  now()),
    ('SCHEDULING',    'Scheduling',    true,  8,  now()),
    ('REPORTING',     'Reporting',     true,  9,  now()),
    ('FINANCE',       'Finance',       true, 10,  now()),
    ('DEV',           'Development',   true, 11,  now()),

    ('GENERAL',       'Unclassified',  true, 12,  now());

create table work_type_alias (
    alias      varchar(60) primary key,
    code       varchar(40) not null references work_type_vocabulary (code),

    source     varchar(10) not null,
    created_at timestamptz not null,

    constraint work_type_alias_source_is_known check (source in ('SEED', 'AUTHORED', 'OBSERVED')),

    constraint work_type_alias_is_not_a_code check (upper(btrim(alias)) <> code)
);

create index work_type_alias_by_code on work_type_alias (code);

comment on table work_type_alias is
    'Alias -> canonical work type. One alias resolves to exactly one type; a word that could mean two is '
    'deliberately absent, because abstaining beats resolving it wrongly.';

insert into work_type_alias (alias, code, source, created_at) values
    ('teaser',        'VIDEO',      'SEED', now()),
    ('promo video',   'VIDEO',      'SEED', now()),
    ('reel',          'VIDEO',      'SEED', now()),
    ('rough cut',     'VIDEO',      'SEED', now()),
    ('shoot',         'PHOTO',      'SEED', now()),
    ('packshot',      'PHOTO',      'SEED', now()),
    ('retouch',       'PHOTO',      'SEED', now()),
    ('caption',       'CONTENT',    'SEED', now()),
    ('copy',          'CONTENT',    'SEED', now()),
    ('article',       'CONTENT',    'SEED', now()),
    ('newsletter',    'CONTENT',    'SEED', now()),
    ('key visual',    'DESIGN',     'SEED', now()),
    ('mood board',    'DESIGN',     'SEED', now()),
    ('layout',        'DESIGN',     'SEED', now()),
    ('campaign',      'ADS',        'SEED', now()),
    ('ad variants',   'ADS',        'SEED', now()),
    ('queue',         'SCHEDULING', 'SEED', now()),
    ('recap',         'REPORTING',  'SEED', now());

create table work_type_family (
    type_a     varchar(40) not null references work_type_vocabulary (code),
    type_b     varchar(40) not null references work_type_vocabulary (code),
    weight     numeric(3, 2) not null,
    version    integer not null default 1,
    created_at timestamptz not null,

    primary key (type_a, type_b, version),

    constraint work_type_family_is_undirected check (type_a < type_b),
    constraint work_type_family_weight_is_a_fraction check (weight > 0 and weight <= 1)
);

comment on table work_type_family is
    'How near two kinds of work are, 0 exclusive to 1. An absent pair scores 0 and is never an error. '
    'Versioned because 10_RISKS section 5 requires re-tuning on real data, and a weight edited in place '
    'would change every historical score with nothing left to compare against.';

insert into work_type_family (type_a, type_b, weight, version, created_at) values

    ('PHOTO',         'VIDEO',       0.70, 1, now()),

    ('CLIENT_INTAKE', 'COORDINATION', 0.60, 1, now()),

    ('ADS',           'DESIGN',      0.55, 1, now()),
    ('DESIGN',        'PHOTO',       0.55, 1, now()),

    ('COORDINATION',  'SCHEDULING',  0.50, 1, now()),
    ('DESIGN',        'VIDEO',       0.45, 1, now()),

    ('ADS',           'REPORTING',   0.40, 1, now()),
    ('FINANCE',       'REPORTING',   0.40, 1, now()),
    ('CONTENT',       'REPORTING',   0.30, 1, now()),
    ('ADS',           'CONTENT',     0.30, 1, now());

alter table task_template
    add column keywords  text[] not null default '{}',

    add column work_type varchar(40) references work_type_vocabulary (code);

create index task_template_by_work_type on task_template (work_type) where work_type is not null;

comment on column task_template.keywords is
    'What people actually call this work. 10_RISKS section 1: recall is 0.036 at zero coverage and 0.881 '
    'at full coverage, so a template with none is invisible to the pipeline rather than weakly matched.';
comment on column task_template.work_type is
    'The governed kind of work, from work_type_vocabulary. Distinct from `type`, which is free text and '
    'stays whatever somebody typed -- this migration does not overwrite what a person wrote.';

alter table process_template
    add column keywords text[] not null default '{}';

comment on column process_template.keywords is
    'As task_template.keywords, for matching a job to a process.';

update task_template
   set work_type = case
       when responsible_role ilike '%agency owner%'    then 'COORDINATION'
       when responsible_role ilike '%account manager%' then 'CLIENT_INTAKE'
       when responsible_role ilike '%content writer%'  then 'CONTENT'
       when responsible_role ilike '%editor%'          then 'CONTENT'
       when responsible_role ilike '%designer%'        then 'DESIGN'
       when responsible_role ilike '%ads specialist%'  then 'ADS'
   end
 where responsible_role is not null
   and work_type is null;
