alter table task_template
    add column responsible_role     varchar(20),
    add column trigger_note         text,
    add column required_input       text,
    add column expected_output      text,
    add column output_kind          varchar(12),
    add column completion_criteria  text;

alter table task_template
    add constraint task_template_output_kind check (
        output_kind is null
            or output_kind in ('TEXT', 'DESIGN', 'REPORT', 'SCHEDULE', 'DECISION', 'PHYSICAL', 'NONE')
    );

alter table task_template
    add constraint task_template_responsible_role check (
        responsible_role is null or responsible_role in ('OWNER', 'MANAGER', 'EMPLOYEE')
    );

comment on column task_template.responsible_role is
    'Who normally does this work, as a role and never as a person (SOP_02 section 10). Null until '
    'somebody is asked, which happens one field at a time on use (SOP-METADATA-01).';
comment on column task_template.output_kind is
    'What kind of thing this work produces. The one metadata field a machine reasons over '
    '(SOP_01 section 3); the rest are for a human to read in a document.';
comment on column task_template.completion_criteria is
    'What "done" means, in words. Read by the SOP rendering; never enforced -- a task is completed by '
    'its assignee saying so, and a criterion the product policed would be a second review gate.';

alter table process_template
    add column trigger_note   text,
    add column end_condition  text,
    add column owner_role     varchar(20);

alter table process_template
    add constraint process_template_owner_role check (
        owner_role is null or owner_role in ('OWNER', 'MANAGER', 'EMPLOYEE')
    );

comment on column process_template.trigger_note is
    'What causes a run of this process to start, in words. Free text rather than a vocabulary: a '
    'taxonomy of triggers is something a business would have to invent before it could describe '
    'itself (SOP_01 section 3).';
comment on column process_template.end_condition is
    'How somebody knows a run has finished. Distinct from every step being closed, which is a fact '
    'about the graph rather than about the business.';

alter table step_definition
    add column optional        boolean not null default false,
    add column condition_note  text;

alter table step_definition
    add constraint step_definition_condition_needs_optional check (
        condition_note is null or optional
    );

comment on column step_definition.optional is
    'Whether this step always applies. False for every existing step, which is what they have always '
    'meant. Asked of the run owner when the step becomes reachable (SOP_01 section 5) -- never at '
    'instantiation, where the answer is frequently unknown and a guess is then wrong for the rest of '
    'the run.';
comment on column step_definition.condition_note is
    'When this step applies, in words -- "is the value above 5,000?". A sentence for a person to '
    'judge, never an expression for the product to evaluate (SOP_01 section 6).';

insert into auth_permission (name, delegable) values
    ('TASK_TEMPLATE_METADATA', false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER', 'TASK_TEMPLATE_METADATA'),
    ('MANAGER', 'TASK_TEMPLATE_METADATA');
