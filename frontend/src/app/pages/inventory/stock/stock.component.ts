import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { InventoryService } from '../../../core/inventory/inventory.service';
import { StockItem, packageEquivalence, stockQuantityText } from '../../../core/inventory/models/inventory.models';
import { CategoryService } from '../../../core/category/category.service';
import { Category } from '../../../core/category/models/category.models';
import { normalizeSearchText as normalizeText } from '../../../shared/utils/product-search';
import { IconComponent } from '../../../shared/components/icon/icon.component';

type StockFilter = 'ALL' | 'CONTROLLED' | 'NEGATIVE' | 'TO_COUNT' | 'NOT_CONTROLLED';

/**
 * INV-01, INV-14, INV-18: the stock of each product, computed from its kardex, with what pending purchases bring and the
 * products still to count. Never edited here: it moves with sales, purchases, counts and adjustments.
 */
@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './stock.component.html',
  styleUrls: ['../inventory-page.css', './stock.component.css']
})
export class StockComponent implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  private readonly categoryService = inject(CategoryService);
  private readonly router = inject(Router);

  readonly items = signal<StockItem[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly isLoading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly categoryId = signal<string>('');
  readonly filter = signal<StockFilter>('ALL');
  readonly search = signal<string>('');

  readonly filters: { value: StockFilter; label: string }[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'CONTROLLED', label: 'Con stock' },
    { value: 'NEGATIVE', label: 'Stock negativo, por revisar' },
    { value: 'TO_COUNT', label: 'Por contar' },
    { value: 'NOT_CONTROLLED', label: 'No controla stock' }
  ];

  readonly visible = computed(() => {
    const term = normalizeText(this.search().trim());
    return this.items().filter(item => {
      const f = this.filter();
      if (f === 'NEGATIVE' && !item.negative) return false;
      if (f !== 'ALL' && f !== 'NEGATIVE' && item.status !== f) return false;
      if (!term) return true;
      return normalizeText(`${item.code} ${item.name} ${item.supplierName ?? ''}`).includes(term);
    });
  });

  readonly totalValue = computed(() => this.items().reduce((sum, i) => sum + (i.value ?? 0), 0));
  readonly toCount = computed(() => this.items().filter(i => i.status === 'TO_COUNT').length);
  readonly negatives = computed(() => this.items().filter(i => i.negative).length);

  ngOnInit(): void {
    this.categoryService.getCategories(undefined, 'ACTIVO').subscribe({
      next: categories => this.categories.set(categories),
      error: () => this.categories.set([])
    });
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.inventoryService.getStock(this.categoryId() || null).subscribe({
      next: items => {
        this.items.set(items);
        this.isLoading.set(false);
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudieron cargar las existencias.');
      }
    });
  }

  onCategory(id: string): void {
    this.categoryId.set(id);
    this.load();
  }

  quantity(item: StockItem): string {
    return item.quantity == null ? '—' : stockQuantityText(item.quantity, item.saleUnit);
  }

  incoming(item: StockItem): string | null {
    return item.incoming > 0 ? `${stockQuantityText(item.incoming, item.saleUnit)} por llegar` : null;
  }

  // CAT-15: «40 = 3 Caja ×12 + 4»
  equivalence(item: StockItem): string | null {
    return item.quantity == null ? null : packageEquivalence(item.quantity, item.saleUnit, item.packages);
  }

  statusLabel(item: StockItem): string {
    if (item.negative) return 'Stock negativo, por revisar';
    switch (item.status) {
      case 'CONTROLLED': return 'Controla stock';
      case 'TO_COUNT': return 'Por contar';
      default: return 'No controla stock';
    }
  }

  openKardex(item: StockItem): void {
    this.router.navigate(['/inventory/kardex'], { queryParams: { product: item.productId } });
  }
}
