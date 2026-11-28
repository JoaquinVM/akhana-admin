import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InventoryService } from '../../../core/inventory/inventory.service';
import {
  ADJUSTMENT_REASONS, Kardex, KardexRow, StockAdjustmentReason, packageEquivalence, stockQuantityText
} from '../../../core/inventory/models/inventory.models';
import { ProductService } from '../../../core/product/product.service';
import { Product } from '../../../core/product/models/product.models';
import { productForEnter, searchProducts } from '../../../shared/utils/product-search';
import { storeMonthStart, storeToday } from '../../../shared/utils/store-date';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { StoreDayPipe } from '../../../shared/utils/store-day.pipe';

/**
 * INV-03: the valued kardex of a product in a range of days (quantity, unit cost and value of each row, and the balance
 * and average after it); INV-02: the adjustment by hand, with its reason.
 */
@Component({
  selector: 'app-kardex',
  standalone: true,
  imports: [CommonModule, FormsModule, StoreDayPipe],
  templateUrl: './kardex.component.html',
  styleUrls: ['../inventory-page.css', './kardex.component.css']
})
export class KardexComponent implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly products = signal<Product[]>([]);
  readonly productSearch = signal('');
  readonly matches = computed(() => this.productSearch().trim()
    ? searchProducts(this.productSearch(), this.products().filter(p => !p.specialType)).slice(0, 20)
    : []);
  readonly productId = signal<string | null>(null);
  readonly from = signal<string>(storeMonthStart());
  readonly to = signal<string>(storeToday());
  readonly kardex = signal<Kardex | null>(null);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly reasons = ADJUSTMENT_REASONS;
  readonly adjusting = signal(false);
  readonly reason = signal<StockAdjustmentReason>('MERMA');
  readonly adjustQuantity = signal<number | null>(null);
  readonly adjustComment = signal('');
  readonly adjustError = signal<string | null>(null);
  readonly isSaving = signal(false);

  readonly equivalence = computed(() => {
    const k = this.kardex();
    return k ? packageEquivalence(k.quantity, k.saleUnit, k.packages) : null;
  });

  ngOnInit(): void {
    this.productService.getProducts().subscribe({ next: products => this.products.set(products), error: () => undefined });
    const product = this.route.snapshot.queryParamMap.get('product');
    if (product) {
      this.productId.set(product);
      this.load();
    }
  }

  choose(product: Product): void {
    this.productSearch.set('');
    this.productId.set(product.id);
    this.router.navigate([], { queryParams: { product: product.id }, replaceUrl: true });
    this.load();
  }

  chooseFromSearch(): void {
    const product = productForEnter(this.productSearch(), this.products(), this.matches());
    if (product) this.choose(product);
  }

  load(): void {
    const id = this.productId();
    if (!id) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.inventoryService.getKardex(id, this.from(), this.to()).subscribe({
      next: kardex => {
        this.kardex.set(kardex);
        this.isLoading.set(false);
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo cargar el kardex.');
      }
    });
  }

  qty(value: number | null | undefined): string {
    const k = this.kardex();
    return value == null || !k ? '—' : stockQuantityText(value, k.saleUnit);
  }

  rowDetail(row: KardexRow): string {
    return [row.reason, row.comment].filter(Boolean).join(' · ');
  }

  openAdjust(): void {
    this.adjusting.set(true);
    this.reason.set('MERMA');
    this.adjustQuantity.set(null);
    this.adjustComment.set('');
    this.adjustError.set(null);
  }

  saveAdjustment(): void {
    const id = this.productId();
    const quantity = Number(this.adjustQuantity());
    if (!id) return;
    if (!(quantity > 0)) {
      this.adjustError.set('La cantidad del ajuste debe ser mayor a 0.');
      return;
    }
    this.isSaving.set(true);
    this.adjustError.set(null);
    this.inventoryService.adjust(id, {
      reason: this.reason(), quantity, comment: this.adjustComment().trim() || null
    }).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.adjusting.set(false);
        this.toast.success('Ajuste registrado.');
        this.load();
      },
      error: err => {
        this.isSaving.set(false);
        this.adjustError.set(err.error?.message || 'No se pudo registrar el ajuste.');
      }
    });
  }
}
