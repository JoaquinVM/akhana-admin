import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PurchaseService } from '../../core/purchase/purchase.service';
import { PurchaseReadingService } from '../../core/purchase/purchase-reading.service';
import { SupplierService } from '../../core/supplier/supplier.service';
import { Supplier } from '../../core/supplier/models/supplier.models';
import {
  DELIVERY_STATUS_LABELS,
  DELIVERY_TYPE_LABELS,
  DeliveryStatus,
  DeliveryType,
  PAYMENT_STATUS_LABELS,
  PaymentAccount,
  Purchase,
  PurchaseDraft,
  PurchasePaymentStatus,
  PurchaseReadingSource,
  PurchaseReadingStatus
} from '../../core/purchase/models/purchase.models';
import { PurchaseFormModalComponent } from './components/purchase-form-modal/purchase-form-modal.component';
import { PurchaseDetailModalComponent } from './components/purchase-detail-modal/purchase-detail-modal.component';
import { PayablesViewComponent } from './components/payables-view/payables-view.component';
import { DraftRead, PurchaseReadingModalComponent } from './components/purchase-reading-modal/purchase-reading-modal.component';
import { CreditorService } from '../../core/debt/creditor.service';
import { Creditor } from '../../core/debt/models/debt.models';
import { StoreDayPipe } from '../../shared/utils/store-day.pipe';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ToastService } from '../../shared/components/toast/toast.service';

// 'ANULADO' filters by purchase status (COM-09); the rest by payment status of the registered ones
export type PaymentStatusFilter = 'ALL' | PurchasePaymentStatus | 'ANULADO';

export type PurchasesTab = 'ORDERS' | 'PAYABLES';

// Rows drawn at a time: the history has hundreds of purchases and drawing them all at once is slow on a phone
export const PAGE_SIZE = 100;

@Component({
  selector: 'app-purchases',
  standalone: true,
  imports: [IconComponent, StoreDayPipe, CommonModule, FormsModule, PurchaseFormModalComponent, PurchaseDetailModalComponent, PayablesViewComponent, PurchaseReadingModalComponent],
  templateUrl: './purchases.component.html',
  styleUrls: ['./purchases.component.css']
})
export class PurchasesComponent implements OnInit {
  private readonly purchaseService = inject(PurchaseService);
  private readonly supplierService = inject(SupplierService);
  private readonly creditorService = inject(CreditorService);
  private readonly readingService = inject(PurchaseReadingService);
  private readonly toast = inject(ToastService);

  readonly statusFilters: { value: PaymentStatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'POR_PAGAR', label: PAYMENT_STATUS_LABELS.POR_PAGAR },
    { value: 'A_CUENTA', label: PAYMENT_STATUS_LABELS.A_CUENTA },
    { value: 'CONSIGNACION', label: PAYMENT_STATUS_LABELS.CONSIGNACION },
    { value: 'PAGADO', label: PAYMENT_STATUS_LABELS.PAGADO },
    { value: 'ANULADO', label: 'Anulado' }
  ];
  readonly deliveryStatuses = Object.entries(DELIVERY_STATUS_LABELS) as [DeliveryStatus, string][];

  activeTab = signal<PurchasesTab>('ORDERS');
  deliveryFilter = signal<DeliveryStatus | ''>('');
  // Changes so the payables view reloads (COM-08)
  payablesRefresh = signal<number>(0);

  purchases = signal<Purchase[]>([]);
  visibleCount = signal<number>(PAGE_SIZE);
  visiblePurchases = computed(() => this.purchases().slice(0, this.visibleCount()));
  hiddenCount = computed(() => Math.max(0, this.purchases().length - this.visibleCount()));
  suppliers = signal<Supplier[]>([]);
  accounts = signal<PaymentAccount[]>([]);
  // DEU-02: only active people pay purchases
  creditors = signal<Creditor[]>([]);
  supplierFilter = signal<string>('');
  statusFilter = signal<PaymentStatusFilter>('ALL');
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  isFormOpen = signal<boolean>(false);
  // COM-11: the purchase being edited in the form; null registers a new one
  editPurchase = signal<Purchase | null>(null);
  detailPurchaseId = signal<string | null>(null);

  // COM-20: Desde foto and Desde texto; a failed status only hides them, and the manual form stays the same
  readingStatus = signal<PurchaseReadingStatus | null>(null);
  readingMode = signal<PurchaseReadingSource | null>(null);
  // CA-09: the purchase whose arrival is being read
  readingFor = signal<Purchase | null>(null);
  formDraft = signal<PurchaseDraft | null>(null);
  formArrival = signal<boolean>(false);
  // CA-21: uploaded only once the purchase is registered, so a photo left unregistered is never stored
  private formPhoto = signal<File | null>(null);

  ngOnInit(): void {
    this.loadReadingStatus();
    this.supplierService.getSuppliers().subscribe({
      next: suppliers => this.suppliers.set(suppliers),
      error: () => this.errorMessage.set('No se pudieron cargar los proveedores.')
    });
    this.purchaseService.getPaymentAccounts().subscribe({
      next: accounts => this.accounts.set(accounts),
      error: () => this.errorMessage.set('No se pudieron cargar las cuentas para pagar.')
    });
    this.creditorService.getCreditors('ACTIVO').subscribe({
      next: creditors => this.creditors.set(creditors),
      error: () => this.errorMessage.set('No se pudieron cargar las personas que pagan pedidos.')
    });
    this.loadPurchases();
  }

  loadPurchases(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    const status = this.statusFilter();
    this.purchaseService.getPurchases({
      supplierId: this.supplierFilter() || null,
      status: status === 'ANULADO' ? 'ANULADO' : null,
      paymentStatus: status === 'ALL' || status === 'ANULADO' ? null : status,
      deliveryStatus: this.deliveryFilter() || null,
      search: this.searchTerm()
    }).subscribe({
      next: purchases => {
        this.purchases.set(purchases);
        this.visibleCount.set(PAGE_SIZE);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los pedidos. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  showMore(): void {
    this.visibleCount.update((count) => count + PAGE_SIZE);
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

  onDeliveryFilter(status: DeliveryStatus | ''): void {
    this.deliveryFilter.set(status);
    this.loadPurchases();
  }

  setTab(tab: PurchasesTab): void {
    this.activeTab.set(tab);
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.loadPurchases();
  }

  statusLabel(status: PurchasePaymentStatus): string {
    return PAYMENT_STATUS_LABELS[status];
  }

  deliveryTypeLabel(type: DeliveryType): string {
    return DELIVERY_TYPE_LABELS[type];
  }

  deliveryStatusLabel(status: DeliveryStatus): string {
    return DELIVERY_STATUS_LABELS[status];
  }

  private loadReadingStatus(): void {
    this.readingService.getStatus().subscribe({
      next: status => this.readingStatus.set(status),
      error: () => this.readingStatus.set(null)
    });
  }

  openForm(): void {
    this.editPurchase.set(null);
    this.clearFormReading();
    this.isFormOpen.set(true);
  }

  private clearFormReading(): void {
    this.formDraft.set(null);
    this.formArrival.set(false);
    this.formPhoto.set(null);
  }

  openReading(mode: PurchaseReadingSource): void {
    this.readingFor.set(null);
    this.readingMode.set(mode);
  }

  closeReading(): void {
    const arrivalOf = this.readingFor();
    this.readingMode.set(null);
    this.readingFor.set(null);
    this.loadReadingStatus();
    if (arrivalOf) {
      this.detailPurchaseId.set(arrivalOf.id);
    }
  }

  onDraftRead(read: DraftRead): void {
    const arrivalOf = this.readingFor();
    this.readingMode.set(null);
    this.readingFor.set(null);
    this.loadReadingStatus();
    if (arrivalOf) {
      this.openArrival(arrivalOf, read);
    } else if (read.completePurchaseId) {
      this.purchaseService.getPurchaseById(read.completePurchaseId).subscribe({
        next: purchase => this.openArrival(purchase, read),
        error: err => this.errorMessage.set(err.error?.message || 'No se pudo cargar el pedido.')
      });
    } else {
      this.editPurchase.set(null);
      this.formDraft.set(read.draft);
      this.formArrival.set(false);
      this.formPhoto.set(read.file);
      this.isFormOpen.set(true);
    }
  }

  onFillByHand(): void {
    const arrivalOf = this.readingFor();
    this.readingMode.set(null);
    this.readingFor.set(null);
    if (arrivalOf) {
      this.openArrival(arrivalOf, null);
    } else {
      this.openForm();
    }
  }

  // CA-09, CA-10: the arrival edits the purchase, filled by the photo or by hand
  private openArrival(purchase: Purchase, read: DraftRead | null): void {
    this.editPurchase.set(purchase);
    this.formDraft.set(read?.draft ?? null);
    this.formArrival.set(true);
    this.formPhoto.set(read?.file ?? null);
    this.isFormOpen.set(true);
  }

  onArrived(event: { purchase: Purchase; withPhoto: boolean }): void {
    this.detailPurchaseId.set(null);
    if (event.withPhoto) {
      this.readingFor.set(event.purchase);
      this.readingMode.set('PHOTO');
    } else {
      this.openArrival(event.purchase, null);
    }
  }

  // COM-11: the form replaces the detail while editing and gives it back on close
  openEdit(purchase: Purchase): void {
    this.detailPurchaseId.set(null);
    this.clearFormReading();
    this.editPurchase.set(purchase);
    this.isFormOpen.set(true);
  }

  closeForm(): void {
    const edited = this.editPurchase();
    this.isFormOpen.set(false);
    this.editPurchase.set(null);
    this.clearFormReading();
    if (edited) {
      this.detailPurchaseId.set(edited.id);
    }
  }

  onPurchaseSaved(purchase: Purchase): void {
    const photo = this.formPhoto();
    this.isFormOpen.set(false);
    this.editPurchase.set(null);
    this.clearFormReading();
    this.loadPurchases();
    if (purchase.revisions?.length) {
      this.payablesRefresh.update(n => n + 1);
    }
    if (!photo) {
      this.detailPurchaseId.set(purchase.id);
      return;
    }
    // The purchase is already registered: if the photo fails, the detail lets it be added again
    this.readingService.uploadPhoto(purchase.id, photo).subscribe({
      next: () => this.detailPurchaseId.set(purchase.id),
      error: err => {
        this.toast.error(err.error?.message || 'El pedido se registró, pero no se pudo guardar la foto. Agrégala desde el detalle.');
        this.detailPurchaseId.set(purchase.id);
      }
    });
  }

  openDetail(purchase: Purchase): void {
    this.detailPurchaseId.set(purchase.id);
  }

  onUpdated(updated: Purchase): void {
    this.purchases.update(list => list.map(p => (p.id === updated.id ? { ...updated, items: [], payments: [] } : p)));
    this.payablesRefresh.update(n => n + 1);
  }
}
