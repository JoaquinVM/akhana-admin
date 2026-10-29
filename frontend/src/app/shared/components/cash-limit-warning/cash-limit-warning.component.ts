import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { CashService } from '../../../core/cash/cash.service';

/**
 * Warning (not blocking) when money leaving Caja exceeds the expected cash of the open session (CAJ-03).
 * Shown only when the money leaves Caja; the server computes the expected cash.
 */
@Component({
  selector: 'app-cash-limit-warning',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './cash-limit-warning.component.html',
  styleUrls: ['./cash-limit-warning.component.css']
})
export class CashLimitWarningComponent implements OnInit {
  private readonly cashService = inject(CashService);

  private readonly amountSignal = signal<number | null>(null);
  private readonly fromCashSignal = signal<boolean>(true);

  @Input() set amount(value: number | string | null | undefined) {
    const n = value === null || value === undefined || value === '' ? null : Number(value);
    this.amountSignal.set(n !== null && !isNaN(n) ? n : null);
  }
  /** False when the money does not leave Caja (another account or a person). */
  @Input() set fromCash(value: boolean) {
    this.fromCashSignal.set(value);
  }

  readonly expectedCash = computed(() => this.cashService.currentSession()?.expectedCash ?? null);
  readonly exceeds = computed(() => {
    const amount = this.amountSignal();
    const expected = this.expectedCash();
    return this.fromCashSignal() && amount !== null && expected !== null
      && Math.round(amount * 100) > Math.round(expected * 100);
  });

  ngOnInit(): void {
    // The expected cash changes with each sale or expense: it is fetched when the form opens. Without an open session there is no warning.
    this.cashService.getCurrentSession().subscribe({ error: () => undefined });
  }
}
