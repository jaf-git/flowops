alter table workspace_settings

    add column closure_coverage_threshold_percent integer not null default 90,

    add column template_idle_window_days integer not null default 90;

comment on column workspace_settings.closure_coverage_threshold_percent is
    'DECISION-SAMPLE-DISCLOSURE-01: below this closure share, an insight states what it could not see.';

comment on column workspace_settings.template_idle_window_days is
    'DECISION-CADENCE-WINDOW-01: the fallback window, used where a template shows no detectable cadence.';
