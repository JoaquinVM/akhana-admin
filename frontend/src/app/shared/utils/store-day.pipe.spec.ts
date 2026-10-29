import { StoreDayPipe } from './store-day.pipe';

describe('StoreDayPipe', () => {
  it('muestra el día tal como viene, sin zona horaria', () => {
    const pipe = new StoreDayPipe();
    expect(pipe.transform('2026-10-29')).toBe('29/10/2026');
    expect(pipe.transform('2026-01-01')).toBe('01/01/2026');
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform('29/10/2026')).toBe('');
  });
});
