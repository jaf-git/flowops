import type { Section } from './sections';

export type DestinationId =
  | 'today'
  | 'work'
  | 'processes'
  | 'pipeline'
  | 'discovery'
  | 'timeline'
  | 'reports'
  | 'chat'
  | 'people'
  | 'settings'
  | 'account';

export interface View {
  readonly section: Section;

  readonly label: string;

  readonly needs?: string;
}

export interface Destination {
  readonly id: DestinationId;

  readonly glyph: string;

  readonly label: string;

  readonly needs?: string;

  readonly place: 'primary' | 'footer';
  readonly views: readonly View[];
}

export const DESTINATIONS: readonly Destination[] = [
  {
    id: 'today',
    glyph: '◈',
    label: 'today',
    place: 'primary',
    views: [{ section: 'today', label: 'today' }],
  },

  {
    id: 'work',
    glyph: '▤',
    label: 'work',
    place: 'primary',
    views: [
      { section: 'tasks', label: 'allWork' },
      { section: 'myWork', label: 'myWork' },
    ],
  },

  {
    id: 'processes',
    glyph: '✦',
    label: 'processes',
    needs: 'PROCESS_VIEW_OWN',
    place: 'primary',
    views: [
      { section: 'processes', label: 'runs' },
      { section: 'templates', label: 'templates', needs: 'TASK_TEMPLATE_VIEW' },
      { section: 'canvas', label: 'board' },
    ],
  },

  {
    id: 'pipeline',
    glyph: '≡',
    label: 'pipeline',

    needs: 'PIPELINE_RUN_VIEW',
    place: 'primary',
    views: [
      { section: 'pipeline', label: 'board' },

      { section: 'findings', label: 'findings' },

      { section: 'guidance', label: 'guidance' },
    ],
  },

  {
    id: 'discovery',
    glyph: '◆',
    label: 'discovery',
    needs: 'DISCOVERY_CANVAS_VIEW',
    place: 'primary',
    views: [
      { section: 'discovery', label: 'canvas' },
      { section: 'engagement', label: 'engagement' },
      { section: 'weekly', label: 'weekly' },
    ],
  },

  {
    id: 'timeline',
    glyph: '◍',
    label: 'timeline',
    needs: 'WORK_NODE_MARK',
    place: 'primary',
    views: [{ section: 'timeline', label: 'timeline' }],
  },

  {
    id: 'reports',
    glyph: '▦',
    label: 'reports',
    place: 'primary',
    views: [{ section: 'reports', label: 'reports' }],
  },

  {
    id: 'chat',
    glyph: '✻',
    label: 'chat',
    place: 'primary',
    views: [{ section: 'chat', label: 'chat' }],
  },

  {
    id: 'people',
    glyph: '☰',
    label: 'people',
    place: 'footer',
    views: [{ section: 'people', label: 'people' }],
  },
  {
    id: 'settings',
    glyph: '⚙',
    label: 'settings',
    needs: 'WORKSPACE_CONFIGURE',
    place: 'footer',
    views: [
      { section: 'settings', label: 'settings' },

      { section: 'organisation', label: 'organisation' },
    ],
  },
  {
    id: 'account',
    glyph: '◉',
    label: 'account',
    place: 'footer',
    views: [{ section: 'account', label: 'account' }],
  },
];

const DESTINATION_OF = new Map<Section, DestinationId>(
  DESTINATIONS.flatMap((destination) =>
    destination.views.map((view) => [view.section, destination.id] as const),
  ),
);

export function destinationOf(section: Section): DestinationId | undefined {
  return DESTINATION_OF.get(section);
}

export function reachable(permissions: readonly string[]): readonly Destination[] {
  const held = (needs: string | undefined): boolean =>
    needs === undefined || permissions.includes(needs);

  return DESTINATIONS.filter((destination) => held(destination.needs))
    .map((destination) => ({
      ...destination,
      views: destination.views.filter((view) => held(view.needs)),
    }))
    .filter((destination) => destination.views.length > 0);
}
