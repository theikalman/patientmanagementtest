import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';

import { AuDateAdapter } from './au-date-adapter';

describe('AuDateAdapter', () => {
  let adapter: AuDateAdapter;

  // Wired exactly as app.config.ts wires it, so the test also covers the provider setup.
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideNativeDateAdapter(),
        { provide: DateAdapter, useClass: AuDateAdapter },
        { provide: MAT_DATE_LOCALE, useValue: 'en-AU' },
      ],
    });
    adapter = TestBed.inject(DateAdapter) as AuDateAdapter;
  });

  const parts = (date: Date | null) =>
    date && !Number.isNaN(date.getTime())
      ? [date.getFullYear(), date.getMonth() + 1, date.getDate()]
      : null;

  it('reads a typed date day first, not month first', () => {
    // The whole reason this class exists: the native adapter reads this as 4 December.
    expect(parts(adapter.parse('12/04/1985'))).toEqual([1985, 4, 12]);
  });

  it('accepts dates without leading zeros', () => {
    expect(parts(adapter.parse('1/2/2020'))).toEqual([2020, 2, 1]);
  });

  it.each(['12-04-1985', '12.04.1985', '12/04/1985'])(
    'accepts "%s" as a separator style',
    (input) => {
      expect(parts(adapter.parse(input))).toEqual([1985, 4, 12]);
    },
  );

  it('expands a two digit year to the most recent past century', () => {
    expect(parts(adapter.parse('12/04/85'))).toEqual([1985, 4, 12]);
    expect(parts(adapter.parse('12/04/20'))).toEqual([2020, 4, 12]);
  });

  it('rejects a day that does not exist rather than rolling it over', () => {
    // new Date(2023, 1, 31) would silently become 3 March.
    expect(parts(adapter.parse('31/02/2023'))).toBeNull();
    expect(parts(adapter.parse('32/01/2023'))).toBeNull();
    expect(parts(adapter.parse('12/13/2023'))).toBeNull();
  });

  it('accepts 29 February in a leap year', () => {
    expect(parts(adapter.parse('29/02/2024'))).toEqual([2024, 2, 29]);
  });

  it('leaves non day-first input to the base adapter', () => {
    expect(parts(adapter.parse('1985-04-12'))).toEqual([1985, 4, 12]);
  });

  it('returns null for an empty value', () => {
    expect(adapter.parse('')).toBeNull();
  });
});
