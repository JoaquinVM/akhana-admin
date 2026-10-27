import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PAYMENT_METHOD_LABELS, PaymentMethod, Sale } from '../../../core/sale/models/sale.models';

// Listado de ventas reutilizable (POS-09): historial y, en modo con anulación, la sesión del POS
@Component({
  selector: 'app-sales-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sales-table.component.html',
  styleUrls: ['./sales-table.component.css']
})
export class SalesTableComponent {
  sales = input<Sale[]>([]);
  // Muestra la columna Caja (historial: ventas de varias sesiones)
  showSession = input<boolean>(false);
  // Muestra el botón Anular; si hay motivo de bloqueo, queda deshabilitado con ese título
  showVoid = input<boolean>(false);
  voidBlockedReason = input<string | null>(null);

  viewDetail = output<Sale>();
  voidSale = output<Sale>();

  methodLabel(method: PaymentMethod): string {
    return PAYMENT_METHOD_LABELS[method] ?? method;
  }

  // Venta pagada en parte en el momento y en parte a crédito (DEU-05)
  hasPartialCredit(sale: Sale): boolean {
    return sale.paymentMethod !== 'CREDITO' && (sale.creditAmount ?? 0) > 0;
  }

  canVoid(sale: Sale): boolean {
    return sale.status !== 'ANULADA' && !sale.historical && !this.voidBlockedReason();
  }

  voidTitle(sale: Sale): string {
    if (sale.status === 'ANULADA') {
      return 'Esta venta ya se encuentra anulada';
    }
    if (sale.historical) {
      return 'Venta histórica de la planilla: no se anula';
    }
    return this.voidBlockedReason() ?? 'Anular venta';
  }

  onVoid(sale: Sale): void {
    if (this.canVoid(sale)) {
      this.voidSale.emit(sale);
    }
  }
}
