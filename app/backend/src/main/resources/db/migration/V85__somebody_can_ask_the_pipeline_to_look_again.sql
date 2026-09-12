insert into auth_permission (name, delegable) values
    ('PIPELINE_RUN_START', false),
    ('PIPELINE_RUN_VIEW',  false);

insert into auth_role_permission (role_name, permission_name) values
    ('OWNER',   'PIPELINE_RUN_START'),
    ('MANAGER', 'PIPELINE_RUN_START'),
    ('OWNER',   'PIPELINE_RUN_VIEW'),
    ('MANAGER', 'PIPELINE_RUN_VIEW');

comment on table pipeline_decision is
    'One row per decision per run, abstentions included -- precision is measurable from what the '
    'pipeline said, recall only from what it did not. `outcome` is the human''s and the pipeline never '
    'writes it (R12.3). Read behind PIPELINE_RUN_VIEW.';
