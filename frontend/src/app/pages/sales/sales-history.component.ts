import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SaleService } from '../../core/sale/sale.service';
import {
  PAYMENT_METHOD_LABELS,
  PaymentMethod,
  Sale,
  SaleStatus,
  SaleType
} from '../../core/sale/models/sale.models';
import { SalesTableComponent } from '../../shared/components/sales-table/sales-table.component';
import { SaleDetailModalComponent } from '../../shared/components/sale-detail-modal/sale-detail-modal.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

export type SaleStatusFilter = 'ALL' | SaleStatus;

// POS-09: sales history of every session, paginated on the server
@Component({
  selector: 'app-sales-history',
  standalone: true,
  imports: [IconComponent, CommonModule, FormsModule, SalesTableComponent, SaleDetailModalComponent],
  templateUrl: './sales-history.component.html',
  styleUrls: ['./sales-history.component.css']
})
export class SalesHistoryComponent implements OnInit {
  private readonly saleService = inject(SaleService);

  readonly pageSize = 20;

  readonly statusFilters: { value: SaleStatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todas' },
    { value: 'COMPLETADA', label: 'Completadas' },
    { value: 'ANULADA', label: 'Anuladas' }
  ];

  readonly saleTypes: { value: SaleType; label: string }[] = [
    { value: 'NORMAL', label: 'Normal' },
    { value: 'AL_COSTO', label: 'Al costo' }
  ];

  readonly paymentMethods = (Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[])
    .map(value => ({ value, label: PAYMENT_METHOD_LABELS[value] }));

  sales = signal<Sale[]>([]);
  sellers = signal<string[]>([]);
  statusFilter = signal<SaleStatusFilter>('ALL');
  fromDate = signal<string>('');
  toDate = signal<string>('');
  saleType = signal<SaleType | ''>('');
  paymentMethod = signal<PaymentMethod | ''>('');
  createdBy = signal<string>('');
  pendingAssignment = signal<boolean>(false);
  currentPage = signal<number>(0);
  totalPages = signal<number>(0);
  totalElements = signal<number>(0);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  detailSale = signal<Sale | null>(null);

  ngOnInit(): void {
    this.saleService.getSellers().subscribe({
      next: sellers => this.sellers.set(sellers),
      error: () => this.sellers.set([])
    });
    this.loadSales();
  }

  loadSales(page = 0): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    const status = this.statusFilter();
    this.saleService.searchSales({
      from: this.fromDate() || null,
      to: this.toDate() || null,
      status: status === 'ALL' ? null : status,
      saleType: this.saleType() || null,
      paymentMethod: this.paymentMethod() || null,
      createdBy: this.createdBy() || null,
      pendingAssignment: this.pendingAssignment()
    }, page, this.pageSize).subscribe({
      next: response => {
        this.sales.set(response.content);
        this.currentPage.set(response.number);
        this.totalPages.set(response.totalPages);
        this.totalElements.set(response.totalElements);
        this.isLoading.set(false);
      },
      error: err => {
        this.sales.set([]);
        this.errorMessage.set(err?.error?.message || 'Error al cargar las ventas. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: SaleStatusFilter): void {
    if (this.statusFilter() !== filter) {
      this.statusFilter.set(filter);
      this.loadSales();
    }
  }

  onFromDate(value: string): void {
    this.fromDate.set(value);
    this.loadSales();
  }

  onToDate(value: string): void {
    this.toDate.set(value);
    this.loadSales();
  }

  onSaleType(value: SaleType | ''): void {
    this.saleType.set(value);
    this.loadSales();
  }

  onPaymentMethod(value: PaymentMethod | ''): void {
    this.paymentMethod.set(value);
    this.loadSales();
  }

  onCreatedBy(value: string): void {
    this.createdBy.set(value);
    this.loadSales();
  }

  onPendingAssignment(value: boolean): void {
    this.pendingAssignment.set(value);
    this.loadSales();
  }

  // POS-12: assigning the last line takes the sale out of pending assignment
  onSaleUpdated(sale: Sale): void {
    if (this.pendingAssignment() && !(sale.items ?? []).some(i => i.assignmentStatus === 'POR_ASIGNAR')) {
      this.loadSales(this.currentPage());
    }
  }

  hasFilters(): boolean {
    return this.statusFilter() !== 'ALL' || !!this.fromDate() || !!this.toDate()
      || !!this.saleType() || !!this.paymentMethod() || !!this.createdBy() || this.pendingAssignment();
  }

  clearFilters(): void {
    this.statusFilter.set('ALL');
    this.fromDate.set('');
    this.toDate.set('');
    this.saleType.set('');
    this.paymentMethod.set('');
    this.createdBy.set('');
    this.pendingAssignment.set(false);
    this.loadSales();
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages() && page !== this.currentPage()) {
      this.loadSales(page);
    }
  }

  openDetail(sale: Sale): void {
    this.detailSale.set(sale);
  }
}
