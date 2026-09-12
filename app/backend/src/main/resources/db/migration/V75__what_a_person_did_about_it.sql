alter table analysis_recommendation
    add column produced_template_id uuid;

comment on column analysis_recommendation.produced_template_id is
    'The process template a WRITE_IT_DOWN act authored. Null for every other kind, and for proposals '
    'nobody has decided. Deliberately NOT a foreign key: the template lives in the application zone and '
    'this row lives in the observation zone, and DECISION-DISCOVERY-ZONE-01 says the two do not share '
    'tables. A retired or deleted template must not take an observation record with it.';

alter table analysis_recommendation
    add constraint analysis_recommendation_produced_only_when_acted
        check (produced_template_id is null or acted_on_at is not null);

alter table analysis_recommendation
    add column dismissed_by uuid references auth_user (id);

comment on column analysis_recommendation.dismissed_by is
    'Who put this proposal aside. Attribution so somebody can be asked, never a figure about them: '
    'nothing in this zone counts, ranks or rates a person (invariant 14).';
