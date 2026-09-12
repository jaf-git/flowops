export function detectedTimezone(): string | undefined {
  try {
    const reported = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return reported === '' ? undefined : reported;
  } catch {
    return undefined;
  }
}

export function preselectedTimezone(available: readonly string[], suggested: string): string {
  const reported = detectedTimezone();
  return reported !== undefined && available.includes(reported) ? reported : suggested;
}

export function timezoneWasDetected(available: readonly string[]): boolean {
  const reported = detectedTimezone();
  return reported !== undefined && available.includes(reported);
}
