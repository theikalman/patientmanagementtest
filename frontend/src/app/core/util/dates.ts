/**
 * Date helpers for the boundary between the Material datepicker (which works in
 * JavaScript `Date`) and the API (which uses ISO-8601 local dates).
 *
 * Both conversions deliberately avoid `Date.toISOString()` and `new Date(string)`:
 * those go through UTC, so a birthday entered as 1 January in Sydney comes back
 * as 31 December. Building the value from the local calendar fields instead keeps
 * the date the user actually picked.
 */

/** "1985-04-12" to a local Date at midnight. */
export function parseIsoDate(iso: string | null | undefined): Date | null {
  if (!iso) {
    return null;
  }
  const [year, month, day] = iso.split('-').map(Number);
  if (!year || !month || !day) {
    return null;
  }
  return new Date(year, month - 1, day);
}

/** A Date to "1985-04-12", using its local calendar fields. */
export function toIsoDate(date: Date | null | undefined): string {
  if (!date) {
    return '';
  }
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
