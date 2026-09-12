/**
 * What the dismiss button says, given the kind of finding it sits under.
 *
 * Dismissal is one mechanism and it means different things. For most findings the honest reading is
 * that the reader is not acting on it yet. For the one a merge raises about the templates it left
 * behind, the reader is making a judgement — the templates still describe real work — and a button
 * saying "not now" would misrecord it as a deferral. Same mechanism, same persistence; only the
 * wording follows the finding.
 *
 * Derived from the finding's own kind rather than from a field the backend would have to carry,
 * because a label is a thing this layer decides.
 */
export function dismissalFor(kind: string): string {
  return kind === 'templates_left_by_a_merge' ? 'findings.stillCorrect' : 'findings.notNow';
}
