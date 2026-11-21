import { Pipe, PipeTransform } from '@angular/core';

// H20: a count with its noun in singular or plural: 1 venta, 3 ventas, 0 ventas
export function plural(count: number | null | undefined, one: string, many: string): string {
  const n = count ?? 0;
  return `${n} ${n === 1 ? one : many}`;
}

@Pipe({ name: 'plural', standalone: true })
export class PluralPipe implements PipeTransform {
  transform(count: number | null | undefined, one: string, many: string): string {
    return plural(count, one, many);
  }
}
