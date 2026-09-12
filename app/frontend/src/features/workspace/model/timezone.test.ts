import { afterEach, describe, expect, it, vi } from 'vitest';

import { preselectedTimezone, timezoneWasDetected } from './timezone';

describe('preselectedTimezone', () => {
  const available = ['Europe/Bucharest', 'Europe/London', 'UTC'];

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function browserReports(zone: string | undefined): void {
    vi.spyOn(Intl, 'DateTimeFormat').mockReturnValue({
      resolvedOptions: () => ({ timeZone: zone }) as Intl.ResolvedDateTimeFormatOptions,
    } as Intl.DateTimeFormat);
  }

  it('prefers what the browser reports, because only the browser knows where the person is', () => {
    browserReports('Europe/Bucharest');

    expect(preselectedTimezone(available, 'UTC')).toBe('Europe/Bucharest');
  });

  it('falls back to the server suggestion when the browser reports a zone we do not know', () => {
    browserReports('Europe/Atlantis');

    expect(preselectedTimezone(available, 'UTC')).toBe('UTC');
  });

  it('falls back when the browser reports nothing at all', () => {
    browserReports(undefined);

    expect(preselectedTimezone(available, 'Europe/London')).toBe('Europe/London');
  });

  it('always yields something in the offered set', () => {
    browserReports('Europe/Atlantis');

    expect(available).toContain(preselectedTimezone(available, 'UTC'));
  });

  it('says whether detection actually worked, so the field can explain itself', () => {
    browserReports('Europe/Bucharest');
    expect(timezoneWasDetected(available)).toBe(true);

    browserReports('Europe/Atlantis');
    expect(timezoneWasDetected(available)).toBe(false);
  });
});
