export type Section =
  | 'today'
  | 'chat'
  | 'canvas'
  | 'tasks'
  | 'myWork'
  | 'templates'
  | 'processes'
  | 'reports'
  | 'discovery'
  | 'engagement'
  | 'weekly'
  | 'pipeline'
  | 'findings'
  | 'guidance'
  | 'timeline'
  | 'settings'
  | 'organisation'
  | 'people'
  | 'account';

export const RAIL_SECTIONS: ReadonlySet<Section> = new Set<Section>(['today', 'tasks']);
