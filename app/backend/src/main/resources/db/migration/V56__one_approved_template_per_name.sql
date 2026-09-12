create unique index task_template_approved_identity_ix
    on task_template (lower(btrim(title)))
    where status = 'APPROVED';

comment on index task_template_approved_identity_ix is
    'One approved template per name (FND_REQ_DECISION_06 condition 3). Drafts may duplicate on purpose; '
    'the approved library may not, because it is the vocabulary every process template references.';
