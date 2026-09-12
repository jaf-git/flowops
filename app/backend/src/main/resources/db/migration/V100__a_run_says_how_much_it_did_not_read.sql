alter table analysis_run
    add column nodes_in_window integer not null default 0;

update analysis_run
set nodes_in_window = nodes_read
where nodes_in_window = 0;

comment on column analysis_run.nodes_in_window is
    'How much work the run''s window held. Equal to nodes_read unless MAX_NODES_PER_RUN cut the read '
        'short, and then the pair says how much of the window every figure on the run is computed over.';
