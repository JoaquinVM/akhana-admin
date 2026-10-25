import { Component, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PurchaseService } from '../../../../core/purchase/purchase.service';
import { PAYMENT_STATUS_LABELS, Payables, Purchase, PurchasePaymentStatus } from '../../../../core/purchase/models/purchase.models';

// COM-08: pedidos con saldo por proveedor; el saldo lo calcula el servidor
@Component({
  selector: 'app-payables-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payables-view.component.html',
  styleUrls: ['./payables-view.component.css']
})
export class PayablesViewComponent {
  private readonly purchaseService = inject(PurchaseService);

  supplierId = input<string>('');
  // Cambia para volver a cargar tras un pago o una anulación
  refreshKey = input<number>(0);

  open = output<Purchase>();

  payables = signal<Payables | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  constructor() {
    effect(() => {
      this.refreshKey();
      this.load(this.supplierId() || null);
    });
  }

  statusLabel(status: PurchasePaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  private load(supplierId: string | null): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.purchaseService.getPayables(supplierId).subscribe({
      next: payables => {
        this.payables.set(payables);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los pedidos por pagar.');
        this.isLoading.set(false);
      }
    });
  }
}
