export interface SearchableRun {
  id: string;
  ownerName: string | null;
  cards: readonly { id: string; assigneeName: string | null }[];
}

export interface Highlight {
  active: boolean;

  runs: ReadonlySet<string>;

  cards: ReadonlySet<string>;
}

const NOTHING: Highlight = { active: false, runs: new Set(), cards: new Set() };

function folded(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase();
}

export function findPerson(query: string, runs: readonly SearchableRun[]): Highlight {
  const looking = folded(query.trim());
  if (looking === '') {
    return NOTHING;
  }

  const foundRuns = new Set<string>();
  const foundCards = new Set<string>();

  for (const run of runs) {
    const owned = run.ownerName !== null && folded(run.ownerName).includes(looking);
    let holds = false;

    for (const card of run.cards) {
      if (card.assigneeName !== null && folded(card.assigneeName).includes(looking)) {
        foundCards.add(card.id);
        holds = true;
      }
    }

    if (owned || holds) {
      foundRuns.add(run.id);
    }
  }

  return { active: true, runs: foundRuns, cards: foundCards };
}
