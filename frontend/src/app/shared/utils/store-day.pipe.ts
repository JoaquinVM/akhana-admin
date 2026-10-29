import { Pipe, PipeTransform } from '@angular/core';

/**
 * A store day sent by the server as text (yyyy-MM-dd: purchase, delivery, consumption, cutoff dates) shown as
 * dd/MM/yyyy. It is not an instant, so it never goes through a time zone and reads the same on any device.
 */
@Pipe({ name: 'storeDay', standalone: true })
export class StoreDayPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? '');
    return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
  }
}
