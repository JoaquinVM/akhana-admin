import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ConsumptionService } from '../../core/consumption/consumption.service';
import { ProductService } from '../../core/product/product.service';
import { Product, isSpecialProduct } from '../../core/product/models/product.models';
import {
  CONSUMPTION_STATUS_LABELS,
  Consumption,
  ConsumptionStatus
} from '../../core/consumption/models/consumption.models';
import { ConsumptionFormModalComponent } from './components/consumption-form-modal/consumption-form-modal.component';
import { ConsumptionDetailModalComponent } from './components/consumption-detail-modal/consumption-detail-modal.component';
import { StoreDayPipe } from '../../shared/utils/store-day.pipe';
import { IconComponent } from '../../shared/components/icon/icon.component';

export type ConsumptionStatusFilter = 'ALL' | ConsumptionStatus;

// CON-01: internal consumptions valued at cost; they move no money
@Component({
  selector: 'app-consumptions',
  standalone: true,
  imports: [IconComponent, StoreDayPipe, CommonModule, FormsModule, ConsumptionFormModalComponent, ConsumptionDetailModalComponent],
  templateUrl: './consumptions.component.html',
  styleUrls: ['./consumptions.component.css']
})
export class ConsumptionsComponent implements OnInit {
  private readonly consumptionService = inject(ConsumptionService);
  private readonly productService = inject(ProductService);

  readonly statusFilters: { value: ConsumptionStatusFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'REGISTRADO', label: CONSUMPTION_STATUS_LABELS.REGISTRADO },
    { value: 'ANULADO', label: CONSUMPTION_STATUS_LABELS.ANULADO }
  ];

  consumptions = signal<Consumption[]>([]);
  products = signal<Product[]>([]);
  statusFilter = signal<ConsumptionStatusFilter>('ALL');
  fromDate = signal<string>('');
  toDate = signal<string>('');
  productFilter = signal<string>('');
  searchTerm = signal<string>('');
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  isFormOpen = signal<boolean>(false);
  detailId = signal<string | null>(null);

  // An inactive product may still be in the store and consumed; a deleted one may not
  availableProducts = computed(() => this.products()
    .filter(p => p.status !== 'ELIMINADO')
    .sort((a, b) => a.name.localeCompare(b.name, 'es', { sensitivity: 'base' })));

  // Total at cost of the registered consumptions in the list
  visibleTotal = computed(() => this.consumptions()
    .filter(c => c.status === 'REGISTRADO')
    .reduce((sum, c) => sum + c.totalAmount, 0));

  ngOnInit(): void {
    this.productService.getProducts().subscribe({
      // POS-11: special products are not consumed
      next: products => this.products.set(products.filter(p => !isSpecialProduct(p))),
      error: () => this.errorMessage.set('No se pudo cargar el catálogo de productos.')
    });
    this.loadConsumptions();
  }

  loadConsumptions(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    const status = this.statusFilter();
    this.consumptionService.getConsumptions({
      from: this.fromDate() || null,
      to: this.toDate() || null,
      productId: this.productFilter() || null,
      status: status === 'ALL' ? null : status,
      search: this.searchTerm()
    }).subscribe({
      next: consumptions => {
        this.consumptions.set(consumptions);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los consumos. Intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  setStatusFilter(filter: ConsumptionStatusFilter): void {
    if (this.statusFilter() !== filter) {
      this.statusFilter.set(filter);
      this.loadConsumptions();
    }
  }

  onFromDate(value: string): void {
    this.fromDate.set(value);
    this.loadConsumptions();
  }

  onToDate(value: string): void {
    this.toDate.set(value);
    this.loadConsumptions();
  }

  onProductFilter(productId: string): void {
    this.productFilter.set(productId);
    this.loadConsumptions();
  }

  onSearch(term: string): void {
    this.searchTerm.set(term);
    this.loadConsumptions();
  }

  statusLabel(status: ConsumptionStatus): string {
    return CONSUMPTION_STATUS_LABELS[status];
  }

  openForm(): void {
    this.isFormOpen.set(true);
  }

  onSaved(consumption: Consumption): void {
    this.isFormOpen.set(false);
    this.loadConsumptions();
    this.detailId.set(consumption.id);
  }

  openDetail(consumption: Consumption): void {
    this.detailId.set(consumption.id);
  }

  onUpdated(updated: Consumption): void {
    this.consumptions.update(list => list.map(c => (c.id === updated.id ? { ...updated, items: [] } : c)));
  }
}
