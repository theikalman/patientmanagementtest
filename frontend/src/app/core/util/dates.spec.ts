import { parseIsoDate, toIsoDate } from './dates';

describe('date helpers', () => {
  it('parses an ISO date into the same local calendar day', () => {
    const parsed = parseIsoDate('1985-04-12');

    expect(parsed?.getFullYear()).toBe(1985);
    expect(parsed?.getMonth()).toBe(3); // April, zero based
    expect(parsed?.getDate()).toBe(12);
  });

  it('formats a Date back to ISO using its local fields', () => {
    expect(toIsoDate(new Date(1985, 3, 12))).toBe('1985-04-12');
  });

  it('pads single digit months and days', () => {
    expect(toIsoDate(new Date(2001, 0, 5))).toBe('2001-01-05');
  });

  /**
   * The regression this helper exists for: going via toISOString() in a timezone
   * ahead of UTC turns 1 January into 31 December.
   */
  it('round trips without drifting a day', () => {
    for (const iso of ['2000-01-01', '1999-12-31', '2024-02-29', '1985-04-12']) {
      expect(toIsoDate(parseIsoDate(iso))).toBe(iso);
    }
  });

  it('treats null, undefined and empty input as no date', () => {
    expect(parseIsoDate(null)).toBeNull();
    expect(parseIsoDate(undefined)).toBeNull();
    expect(parseIsoDate('')).toBeNull();
    expect(toIsoDate(null)).toBe('');
    expect(toIsoDate(undefined)).toBe('');
  });

  it('rejects a malformed value rather than inventing a date', () => {
    expect(parseIsoDate('not-a-date')).toBeNull();
  });
});
