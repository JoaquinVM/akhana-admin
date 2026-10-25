import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PurchaseService } from '../../core/purchase/purchase.service';
import { SupplierService } from '../../core/supplier/supplier.service';
import { Supplier } from '../../core/supplier/models/supplier.models';
import {
  PAYMENT_STATUS_LABELS,
  PaymentAccount,
  Purchase,
  PurchasePaymentStatus
} from '../../core/purchase/models/purchase.models';
import { PurchaseFormModalComponent } from './components/purchase-form-modal/purchase-form-modal.component';
import { PurchaseDetailModalComponent } from './components/purchase-detail-modal/purchase-detail-modal.component';

export type PaymentStatusFilter = 'ALL' | PurchasePaymentStatus;

@Component({
  selector: 'app-purchases',
  standalone: true,
  imports: [CommonModule, FormsModule, PurchaseFormModalComponent, PurchaseDetailModalComponent],
  templateUrl: './purchases.component.html',
  styleUrls: ['./purchases.component.css']
})
export class PurchasesComponent implements OnInit {
  private readonly purchaseService = inject(PurchaseService);
  private readonly supplierService = inject(SupplierService);

  readonly statusFilters: { value: PaymentStatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'POR_PAGAR', label: PAYMENT_STATUS_LABELS.POR_PAGAR },
    { value: 'A_CUENTA', label: PAYMENT_STATUS_LABELS.A_CUENTA },
    { value: 'CONSIGNACION', label: PAYMENT_STATUS_LABELS.CONSIGNACION },
    { value: 'PAGADO', label: PAYMENT_STATUS_LABELS.PAGADO }
  ];

  purchases = signal<Purchase[]>([]);
  suppliers = signal<Supplier[]>([]);
  accounts = signal<PaymentAccount[]>([]);
  supplierFilter = signal<string>('');
  statusFilter = signal<PaymentStatusFilter>('ALL');
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  isFormOpen = signal<boolean>(false);
  detailPurchaseId = signal<string | null>(null);

  ngOnInit(): void {
    this.supplierService.getSuppliers().subscribe({
      next: suppliers => this.suppliers.set(suppliers),
      error: () => this.errorMessage.set('No se pudieron cargar los proveedores.')
    });
    this.purchaseService.getPaymentAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: () => this.errorMessage.set('No se pudieron cargar las cuentas para pagar.')
    });
    this.loadPurchases();
  }

  loadPurchases(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    const status = this.statusFilter();
    this.purchaseService.getPurchases({
      supplierId: this.supplierFilter() || null,
      paymentStatus: status === 'ALL' ? null : status,
      search: this.searchTerm()
    }).subscribe({
      next: purchases => {
        this.purchases.set(purchases);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los pedidos. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: PaymentStatusFilter): void {
    if (this.statusFilter() !== filter) {
      this.statusFilter.set(filter);
      this.loadPurchases();
    }
  }

  onSupplierFilter(supplierId: string): void {
    this.supplierFilter.set(supplierId);
    this.loadPurchases();
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.loadPurchases();
  }

  statusLabel(status: PurchasePaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  openForm(): void {
    this.isFormOpen.set(true);
  }

  onPurchaseSaved(purchase: Purchase): void {
    this.isFormOpen.set(false);
    this.loadPurchases();
    this.detailPurchaseId.set(purchase.id);
  }

  openDetail(purchase: Purchase): void {
    this.detailPurchaseId.set(purchase.id);
  }

  onPaid(updated: Purchase): void {
    this.purchases.update(list => list.map(p => (p.id === updated.id ? { ...updated, items: [], payments: [] } : p)));
  }
}
