/**
 * The adoption share, as a percentage a reader can trust beside the two counts it is printed with.
 *
 * Rounding alone gives "0% of marks name an activity — 2 of 497", and nought per cent beside two
 * marks is a contradiction the reader has to resolve. Anything above nothing but below one per cent
 * is shown as "<1" instead.
 */
export function adoptionShare(naming: number, marks: number): string {
  const rounded = Math.round((naming / marks) * 100);
  return rounded === 0 && naming > 0 ? '<1' : String(rounded);
}
