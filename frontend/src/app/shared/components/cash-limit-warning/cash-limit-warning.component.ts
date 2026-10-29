import { Component, OnInit, computed, inject, input } from '@angular/core';
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

  // The amount typed in the form; a form control may hand it over as text
  readonly amount = input<number | string | null | undefined>(null);
  /** False when the money does not leave Caja (another account or a person). */
  readonly fromCash = input<boolean>(true);

  private readonly amountValue = computed<number | null>(() => {
    const value = this.amount();
    const n = value === null || value === undefined || value === '' ? null : Number(value);
    return n !== null && !isNaN(n) ? n : null;
  });

  readonly expectedCash = computed(() => this.cashService.currentSession()?.expectedCash ?? null);
  readonly exceeds = computed(() => {
    const amount = this.amountValue();
    const expected = this.expectedCash();
    return this.fromCash() && amount !== null && expected !== null
      && Math.round(amount * 100) > Math.round(expected * 100);
  });

  ngOnInit(): void {
    // The expected cash changes with each sale or expense: it is fetched when the form opens. Without an open session there is no warning.
    this.cashService.getCurrentSession().subscribe({ error: () => undefined });
  }
}
