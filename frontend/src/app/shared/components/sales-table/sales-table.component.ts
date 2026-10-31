import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PAYMENT_METHOD_LABELS, PaymentMethod, Sale } from '../../../core/sale/models/sale.models';
import { operationDayLabel } from '../../utils/store-date';

// Reusable sales list (POS-09): history and, in void mode, the POS session
@Component({
  selector: 'app-sales-table',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sales-table.component.html',
  styleUrls: ['./sales-table.component.css']
})
export class SalesTableComponent {
  sales = input<Sale[]>([]);
  // Shows the Caja column (history: sales of several sessions)
  showSession = input<boolean>(false);
  // Shows the Anular button; with a blocking reason it is disabled with that title
  showVoid = input<boolean>(false);
  voidBlockedReason = input<string | null>(null);

  viewDetail = output<Sale>();
  voidSale = output<Sale>();

  // CAJ-11: «Día 31/10 · cargada el 01/11 00:20» when it counts for another day than the one it was recorded
  dayMark(sale: Sale): string | null {
    return operationDayLabel(sale.operationDate, sale.createdAt);
  }

  methodLabel(method: PaymentMethod): string {
    return PAYMENT_METHOD_LABELS[method] ?? method;
  }

  // Sale paid partly now and partly on credit (DEU-05)
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
