import { Component, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { CashSessionSummary } from '../../../../core/cash/models/cash.models';

// The five figures of the open session; the expected cash comes from the server (CAJ-03)
@Component({
  selector: 'app-pos-kpis',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './pos-kpis.component.html',
  styleUrls: ['./pos-kpis.component.css']
})
export class PosKpisComponent {
  readonly session = input.required<CashSessionSummary>();
  // Active cash expenses of the session (voided ones do not count)
  readonly expensesTotal = input<number>(0);
  readonly expectedCash = input<number>(0);
}
