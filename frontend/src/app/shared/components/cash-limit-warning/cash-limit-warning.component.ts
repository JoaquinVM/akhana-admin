import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { CashService } from '../../../core/cash/cash.service';

/**
 * Aviso (sin bloquear) cuando una salida de Caja supera el efectivo esperado de la caja abierta (CAJ-03).
 * Se muestra solo si la salida es de Caja; el esperado lo calcula el servidor.
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
  /** Falso cuando la salida no es de Caja (otra cuenta o una persona). */
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
    // El esperado cambia con cada venta o gasto: se pide al abrir el formulario. Sin caja abierta no hay aviso.
    this.cashService.getCurrentSession().subscribe({ error: () => undefined });
  }
}
