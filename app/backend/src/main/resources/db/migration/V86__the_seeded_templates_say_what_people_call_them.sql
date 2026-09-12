update task_template set responsible_role = v.role, output_kind = v.kind
  from (values
    ('photograph the new menu items',            'Designer',        'DESIGN'),
    ('crop the header pictures',                 'Designer',        'DESIGN'),
    ('create three squares for instagram',       'Designer',        'DESIGN'),
    ('make the images',                          'Designer',        'DESIGN'),
    ('round up the brand files',                 'Designer',        'DESIGN'),
    ('trim the film to fifteen seconds',         'Designer',        'DESIGN'),
    ('rewrite the leaflet wording',              'Content writer',  'TEXT'),
    ('correct the spelling on the about page',   'Editor',          'TEXT'),
    ('prepare this month''s subscriber email',   'Content writer',  'TEXT'),
    ('one round of edits',                       'Editor',          'TEXT'),
    ('list the pages that need work',            'Content writer',  'TEXT'),
    ('monthly performance report',               'Ads specialist',  'REPORT'),
    ('check last month''s numbers',              'Ads specialist',  'REPORT'),
    ('work out what the adverts cost in july',   'Ads specialist',  'REPORT'),
    ('answer the overnight messages',            'Account manager', 'TEXT'),
    ('chase the client for feedback',            'Account manager', 'TEXT'),
    ('collect their logos and photos',           'Account manager', 'NONE'),
    ('kick-off call',                            'Account manager', 'NONE'),
    ('have the client sign off the wording',     'Account manager', 'DECISION'),
    ('get the client to approve it',             'Account manager', 'DECISION'),
    ('sketch out what we post in december',      'Agency owner',    'SCHEDULE'),
    ('change the shop times on the map listing', 'Account manager', 'NONE'),
    ('list the new items in the online shop',    'Account manager', 'NONE'),
    ('build a one-page site for the promotion',  'Designer',        'DESIGN')
  ) as v(title, role, kind)
 where lower(btrim(task_template.title)) = v.title
   and task_template.responsible_role is null;

update task_template set keywords = v.words
  from (values
    ('crop the header pictures',                 array['brighten them', 'new version', 'banner image', 'resize the photos']),
    ('photograph the new menu items',            array['shoot the dishes', 'photo session', 'new menu photos']),
    ('create three squares for instagram',       array['three squares', 'grid posts', 'carousel set']),
    ('make the images',                          array['every size', 'platform sizes', 'export the assets']),
    ('round up the brand files',                 array['brand colours', 'logo files', 'their assets', 'brand kit']),
    ('trim the film to fifteen seconds',         array['cut it down', 'shorter version', 'fifteen second']),
    ('rewrite the leaflet wording',              array['reword the leaflet', 'new copy for the flyer']),
    ('correct the spelling on the about page',   array['typos on the site', 'proofread the page']),
    ('prepare this month''s subscriber email',   array['newsletter draft', 'mailing list email']),
    ('one round of edits',                       array['second pass', 'editor pass', 'one more look']),
    ('list the pages that need work',            array['page audit', 'which pages', 'site inventory']),
    ('monthly performance report',               array['numbers were up', 'reach and engagement', 'how it performed']),
    ('check last month''s numbers',              array['last month figures', 'what people looked at', 'the stats']),
    ('work out what the adverts cost in july',   array['ad spend', 'what the adverts cost', 'budget for the ads']),
    ('answer the overnight messages',            array['overnight messages', 'morning replies', 'catch up on the inbox']),
    ('chase the client for feedback',            array['waiting on them', 'ask them for', 'no reply yet', 'chase them up']),
    ('collect their logos and photos',           array['files they sent', 'their materials', 'gather the assets']),
    ('kick-off call',                            array['first call', 'intro meeting', 'onboarding call']),
    ('have the client sign off the wording',     array['sign off', 'approve the copy', 'client approval']),
    ('get the client to approve it',             array['they approved', 'client said yes', 'signed off on it']),
    ('sketch out what we post in december',      array['content plan', 'what we post', 'posting calendar']),
    ('change the shop times on the map listing', array['opening hours', 'map listing', 'business profile']),
    ('list the new items in the online shop',    array['product listings', 'shop items', 'add the products']),
    ('build a one-page site for the promotion',  array['landing page', 'one page site', 'promo page'])
  ) as v(title, words)
 where lower(btrim(task_template.title)) = v.title
   and cardinality(task_template.keywords) = 0;

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
