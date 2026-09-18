import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CashRegisterSession, DenominationBreakdown } from '../../models/domain.model';
import { BOLIVIANO_DENOMINATIONS } from '../../services/cash-register.service';

@Component({
  selector: 'app-register-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="isOpen" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs overflow-y-auto">
      <div class="bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant max-w-2xl w-full my-8 overflow-hidden">
        
        <!-- Modal Header -->
        <div class="p-6 bg-inverse-surface text-white flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span class="material-symbols-outlined text-accent-gold text-2xl">
              {{ mode === 'OPEN' ? 'lock_open' : 'point_of_sale' }}
            </span>
            <div>
              <h2 class="text-lg font-bold font-headline">
                {{ mode === 'OPEN' ? 'Apertura de Caja' : 'Arqueo y Cierre de Caja (Turno Diario)' }}
              </h2>
              <p class="text-xs text-outline-variant mt-0.5">
                {{ mode === 'OPEN' ? 'Defina el monto base inicial en efectivo' : 'Conteo físico de denominaciones en Bolivianos (Bs)' }}
              </p>
            </div>
          </div>
          <button (click)="onClose()" class="text-white/70 hover:text-white p-1 rounded-lg hover:bg-white/10 transition-colors">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <!-- Mode OPEN Form -->
        <div *ngIf="mode === 'OPEN'" class="p-6 space-y-4">
          <div>
            <label class="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-1.5">
              Monto Inicial de Apertura (Efectivo en Caja)
            </label>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 pl-3.5 flex items-center text-on-surface-variant font-bold">Bs</span>
              <input
                type="number"
                min="0"
                step="10"
                [(ngModel)]="initialAmount"
                class="w-full pl-12 pr-4 py-3 rounded-xl border border-outline-variant text-xl font-bold font-mono focus:ring-2 focus:ring-primary focus:border-primary bg-surface-container-low"
              />
            </div>
            <p class="text-xs text-on-surface-variant mt-1.5">
              Sugerido para inicio de operaciones: <strong>Bs 200.00</strong>
            </p>
          </div>

          <div>
            <label class="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-1.5">
              Observaciones / Notas de Apertura (Opcional)
            </label>
            <textarea
              [(ngModel)]="notes"
              rows="2"
              placeholder="Ej. Billetes y monedas de cambio provistos por administración..."
              class="w-full p-3 rounded-xl border border-outline-variant text-sm focus:ring-2 focus:ring-primary focus:border-primary bg-surface-container-low"
            ></textarea>
          </div>

          <div class="pt-4 flex justify-end gap-3 border-t border-outline-variant/30">
            <button
              (click)="onClose()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm bg-surface-container hover:bg-surface-container-high transition-colors">
              Cancelar
            </button>
            <button
              (click)="confirmOpen()"
              class="px-6 py-2.5 rounded-xl font-bold text-sm bg-primary hover:bg-primary-container text-white shadow-xs transition-colors flex items-center gap-2">
              <span class="material-symbols-outlined text-base">lock_open</span>
              Abrir Caja Ahora
            </button>
          </div>
        </div>

        <!-- Mode CLOSE Form (Arqueo con Conteo) -->
        <div *ngIf="mode === 'CLOSE'" class="p-6 space-y-6 max-h-[80vh] overflow-y-auto">
          
          <!-- Financial Snapshot Cards -->
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <div class="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/40">
              <span class="text-[11px] font-semibold text-on-surface-variant uppercase">Monto Inicial</span>
              <div class="text-lg font-bold font-mono text-on-surface mt-0.5">Bs {{ currentSession?.initialAmount | number:'1.2-2' }}</div>
            </div>
            <div class="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/40">
              <span class="text-[11px] font-semibold text-on-surface-variant uppercase">Ventas Efectivo</span>
              <div class="text-lg font-bold font-mono text-primary mt-0.5">+Bs {{ currentSession?.cashSalesTotal | number:'1.2-2' }}</div>
            </div>
            <div class="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/40">
              <span class="text-[11px] font-semibold text-on-surface-variant uppercase">Ventas Simple QR</span>
              <div class="text-lg font-bold font-mono text-blue-700 mt-0.5">Bs {{ currentSession?.qrSalesTotal | number:'1.2-2' }}</div>
              <span class="text-[10px] text-on-surface-variant block mt-0.5">(No es físico)</span>
            </div>
            <div class="p-3.5 rounded-xl bg-amber-50 border border-amber-300">
              <span class="text-[11px] font-bold text-amber-900 uppercase">Efectivo Esperado</span>
              <div class="text-lg font-bold font-mono text-amber-900 mt-0.5">Bs {{ currentSession?.expectedCash | number:'1.2-2' }}</div>
            </div>
          </div>

          <!-- Denomination Breakdown Table -->
          <div>
            <div class="flex items-center justify-between mb-2">
              <h4 class="text-sm font-bold text-on-surface uppercase tracking-wider flex items-center gap-1.5">
                <span class="material-symbols-outlined text-primary text-base">payments</span>
                Conteo Físico de Dinero (Bolivianos)
              </h4>
              <button (click)="resetBreakdown()" class="text-xs text-primary font-semibold hover:underline">
                Limpiar Conteo
              </button>
            </div>

            <div class="bg-surface-container-low rounded-xl border border-outline-variant/50 overflow-hidden divide-y divide-outline-variant/30">
              <div
                *ngFor="let item of denominations"
                class="flex items-center justify-between px-4 py-2.5 hover:bg-surface-container transition-colors">
                <div class="flex items-center gap-2">
                  <span
                    [ngClass]="item.isBill ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'"
                    class="text-[11px] font-bold px-2 py-0.5 rounded-md font-mono">
                    {{ item.isBill ? 'Billete' : 'Moneda' }}
                  </span>
                  <span class="text-sm font-semibold text-on-surface">{{ item.label }}</span>
                </div>

                <div class="flex items-center gap-3">
                  <div class="flex items-center">
                    <button
                      type="button"
                      (click)="changeDenomination(item.value, -1)"
                      class="w-7 h-7 bg-surface-container-high rounded-l-md font-bold text-sm hover:bg-surface-variant">-</button>
                    <input
                      type="number"
                      min="0"
                      [(ngModel)]="counts[item.value]"
                      (ngModelChange)="calculateCounted()"
                      class="w-14 h-7 text-center font-bold font-mono text-sm border-y border-outline-variant bg-surface-container-lowest focus:outline-hidden"
                    />
                    <button
                      type="button"
                      (click)="changeDenomination(item.value, 1)"
                      class="w-7 h-7 bg-surface-container-high rounded-r-md font-bold text-sm hover:bg-surface-variant">+</button>
                  </div>
                  <div class="w-20 text-right font-mono text-sm font-bold text-on-surface">
                    Bs {{ (counts[item.value] || 0) * item.value | number:'1.2-2' }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Arqueo Summary & Discrepancy Alert -->
          <div class="p-4 rounded-xl border flex items-center justify-between"
            [ngClass]="difference === 0 ? 'bg-emerald-50 border-emerald-300 text-emerald-900' : (difference < 0 ? 'bg-red-50 border-red-300 text-red-900' : 'bg-blue-50 border-blue-300 text-blue-900')">
            <div>
              <div class="text-xs uppercase font-bold tracking-wider">
                {{ difference === 0 ? '✅ Cuadre Exacto de Caja' : (difference < 0 ? '⚠️ Faltante en Caja' : 'ℹ️ Sobrante en Caja') }}
              </div>
              <div class="text-sm font-medium mt-0.5">
                Efectivo Contado: <strong>Bs {{ totalCounted | number:'1.2-2' }}</strong> | Esperado: <strong>Bs {{ currentSession?.expectedCash | number:'1.2-2' }}</strong>
              </div>
            </div>
            <div class="text-right">
              <div class="text-xs font-semibold uppercase text-on-surface-variant">Diferencia</div>
              <div class="text-xl font-extrabold font-mono" [ngClass]="difference < 0 ? 'text-error' : (difference > 0 ? 'text-blue-700' : 'text-primary')">
                {{ difference >= 0 ? '+' : '' }}Bs {{ difference | number:'1.2-2' }}
              </div>
            </div>
          </div>

          <div>
            <label class="block text-xs font-bold text-on-surface-variant uppercase tracking-wider mb-1.5">
              Notas de Cierre de Caja
            </label>
            <textarea
              [(ngModel)]="notes"
              rows="2"
              placeholder="Detalles sobre el arqueo o justificación de diferencias..."
              class="w-full p-3 rounded-xl border border-outline-variant text-sm focus:ring-2 focus:ring-primary focus:border-primary bg-surface-container-low"
            ></textarea>
          </div>

          <div class="pt-4 flex justify-end gap-3 border-t border-outline-variant/30">
            <button
              (click)="onClose()"
              class="px-5 py-2.5 rounded-xl font-semibold text-sm bg-surface-container hover:bg-surface-container-high transition-colors">
              Cancelar
            </button>
            <button
              (click)="confirmClose()"
              class="px-6 py-2.5 rounded-xl font-bold text-sm bg-error hover:bg-red-800 text-white shadow-xs transition-colors flex items-center gap-2">
              <span class="material-symbols-outlined text-base">lock</span>
              Confirmar y Cerrar Caja
            </button>
          </div>
        </div>

      </div>
    </div>
  `
})
export class RegisterModalComponent implements OnInit {
  @Input() isOpen = false;
  @Input() mode: 'OPEN' | 'CLOSE' = 'OPEN';
  @Input() currentSession: CashRegisterSession | null = null;

  @Output() openRegister = new EventEmitter<{ initialAmount: number; notes?: string }>();
  @Output() closeRegister = new EventEmitter<{ breakdown: DenominationBreakdown; notes?: string }>();
  @Output() modalClosed = new EventEmitter<void>();

  denominations = BOLIVIANO_DENOMINATIONS;
  counts: { [value: number]: number } = {};
  initialAmount = 200.0;
  notes = '';
  totalCounted = 0;
  difference = 0;

  ngOnInit(): void {
    this.resetBreakdown();
  }

  resetBreakdown(): void {
    this.counts = {};
    for (const d of this.denominations) {
      this.counts[d.value] = 0;
    }
    this.calculateCounted();
  }

  changeDenomination(value: number, delta: number): void {
    const current = this.counts[value] || 0;
    this.counts[value] = Math.max(0, current + delta);
    this.calculateCounted();
  }

  calculateCounted(): void {
    let sum = 0;
    for (const d of this.denominations) {
      sum += (this.counts[d.value] || 0) * d.value;
    }
    this.totalCounted = Number(sum.toFixed(2));
    const expected = this.currentSession?.expectedCash || 0;
    this.difference = Number((this.totalCounted - expected).toFixed(2));
  }

  confirmOpen(): void {
    this.openRegister.emit({
      initialAmount: this.initialAmount,
      notes: this.notes
    });
    this.onClose();
  }

  confirmClose(): void {
    const breakdown: DenominationBreakdown = {};
    for (const d of this.denominations) {
      breakdown[d.value.toString()] = this.counts[d.value] || 0;
    }
    this.closeRegister.emit({
      breakdown,
      notes: this.notes
    });
    this.onClose();
  }

  onClose(): void {
    this.isOpen = false;
    this.modalClosed.emit();
  }
}
