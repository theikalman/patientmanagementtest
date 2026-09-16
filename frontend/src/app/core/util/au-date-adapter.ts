import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

/**
 * Date adapter that parses typed dates the Australian way.
 *
 * Angular Material's {@link NativeDateAdapter} formats dates according to
 * `MAT_DATE_LOCALE`, but its `parse()` falls back to `Date.parse()`, which
 * interprets "12/04/1985" as 4 December in the US convention. On an Australian
 * clinical form that silently records the wrong birthday, and the field looks
 * correct afterwards because it is then re-formatted as dd/mm/yyyy.
 *
 * Overriding `parse()` to read day-first is the smallest fix that closes the gap.
 * Only the typed path changes; picking from the calendar was never ambiguous.
 */
@Injectable()
export class AuDateAdapter extends NativeDateAdapter {
  /** d/m/yyyy, d-m-yyyy or d.m.yyyy, with optional leading zeros and a 2 or 4 digit year. */
  private static readonly DAY_FIRST = /^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2}|\d{4})$/;

  override parse(value: unknown): Date | null {
    if (typeof value !== 'string') {
      return super.parse(value);
    }

    const match = AuDateAdapter.DAY_FIRST.exec(value.trim());
    if (!match) {
      return super.parse(value);
    }

    const day = Number(match[1]);
    const month = Number(match[2]);
    const year = this.expandYear(Number(match[3]), match[3].length);

    // Construct from local calendar fields, then verify the parts survived: the
    // Date constructor happily rolls 31/02 over into 3 March, and a date the user
    // did not type should be reported as invalid rather than silently corrected.
    const parsed = new Date(year, month - 1, day);
    const isExact =
      parsed.getFullYear() === year && parsed.getMonth() === month - 1 && parsed.getDate() === day;

    return isExact ? parsed : new Date(NaN);
  }

  /** "85" means 1985, "25" means 2025: a two digit year is never in the future for a birth date. */
  private expandYear(year: number, digits: number): number {
    if (digits === 4) {
      return year;
    }
    const currentTwoDigit = new Date().getFullYear() % 100;
    return year <= currentTwoDigit ? 2000 + year : 1900 + year;
  }
}
