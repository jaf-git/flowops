let offsetFromBrowserClock = 0;

export function rememberServerTime(serverTime: string | undefined): void {
  if (serverTime === undefined) {
    return;
  }
  const server = Date.parse(serverTime);
  if (Number.isNaN(server)) {
    return;
  }
  offsetFromBrowserClock = server - Date.now();
}

export function serverNow(): Date {
  return new Date(Date.now() + offsetFromBrowserClock);
}

export function earliestDeadlineForInput(): string {
  const soonest = new Date(serverNow().getTime() + 60_000);

  const pad = (value: number): string => `${value}`.padStart(2, '0');
  return (
    `${soonest.getFullYear()}-${pad(soonest.getMonth() + 1)}-${pad(soonest.getDate())}` +
    `T${pad(soonest.getHours())}:${pad(soonest.getMinutes())}`
  );
}

export function serverClockOffset(): number {
  return offsetFromBrowserClock;
}

export function resetServerClock(): void {
  offsetFromBrowserClock = 0;
}
