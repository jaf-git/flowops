export function monthsBetween(from: string | null, to: string | null): number {
  if (!from || !to) {
    return 1;
  }
  const start = new Date(from).getTime();
  const end = new Date(to).getTime();
  if (Number.isNaN(start) || Number.isNaN(end) || end <= start) {
    return 1;
  }
  const days = (end - start) / (1000 * 60 * 60 * 24);
  return Math.max(1, Math.round(days / 30));
}
