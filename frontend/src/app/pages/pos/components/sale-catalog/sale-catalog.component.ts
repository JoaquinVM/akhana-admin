import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Product, ProductPresentation, soldPresentations } from '../../../../core/product/models/product.models';
import { QuickProductGroup } from '../../../../core/quick-product/models/quick-product.models';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';
import { SearchableProduct, productForEnter, searchProducts } from '../../../../shared/utils/product-search';
import { ConnectionNoticeComponent } from '../../../../shared/components/connection-notice/connection-notice.component';

// What the catalog picks: a product, or a package of a product by unit that is sold whole (CAT-15)
export interface CatalogPick {
  product: Product;
  presentation: ProductPresentation | null;
}

// A row of the search: the product, or one of its packages with its own name, price and barcode
export interface CatalogEntry extends SearchableProduct, CatalogPick {
  key: string;
  price: number | null;
}

// CAT-15: every product, followed by the packages of a product by unit that are sold whole ("Barra Chia · Caja ×12").
// The packages of a product by kilo stay in its picker (CAT-11)
export function catalogEntries(products: Product[]): CatalogEntry[] {
  const entries: CatalogEntry[] = [];
  for (const product of products) {
    entries.push({
      key: product.id, product, presentation: null, price: product.sellPrice,
      code: product.code, name: product.name, supplierName: product.supplierName, categoryName: product.categoryName,
      tags: product.tags, barcodes: product.barcodes
    });
    if (product.saleUnit === 'KILOGRAM' || product.specialType) continue;
    for (const presentation of soldPresentations(product)) {
      entries.push({
        key: `${product.id}:${presentation.id}`, product, presentation, price: presentation.price,
        code: product.code, name: `${product.name} · ${presentation.name}`, supplierName: product.supplierName,
        categoryName: product.categoryName, tags: product.tags, barcodes: presentation.barcode ? [presentation.barcode] : []
      });
    }
  }
  return entries;
}

/**
 * Left column of the register-sale screen: product search (POS-01) and quick product groups (POS-02).
 * It only picks products or packages; register-sale decides how each one goes into the cart (picker, free line…).
 */
@Component({
  selector: 'app-sale-catalog',
  standalone: true,
  imports: [CommonModule, FormsModule, ConnectionNoticeComponent],
  templateUrl: './sale-catalog.component.html',
  styleUrls: ['./sale-catalog.component.css']
})
export class SaleCatalogComponent {
  readonly quickProductService = inject(QuickProductService);

  // Active products of the catalog
  readonly products = input<Product[]>([]);
  // POS-17: while it loads or if it fails, finding nothing does not mean there is nothing
  readonly catalogLoading = input<boolean>(false);
  readonly catalogFailed = input<boolean>(false);
  // 096: in an at-cost sale the search and the quick products show the cost, the price the line will take
  readonly atCost = input<boolean>(false);

  readonly add = output<CatalogPick>();
  readonly configure = output<void>();
  readonly retryCatalog = output<void>();
  readonly retryQuickGroups = output<void>();

  readonly productSearchTerm = signal<string>('');
  readonly activeGroupId = signal<string | null>(null);

  readonly activeGroup = computed<QuickProductGroup | null>(() => {
    const groups = this.quickProductService.groups();
    if (groups.length === 0) return null;
    const currentId = this.activeGroupId();
    if (!currentId) return groups[0];
    return groups.find((g) => g.id === currentId) ?? groups[0];
  });

  readonly entries = computed<CatalogEntry[]>(() => catalogEntries(this.products()));

  // Every match, best first; the dropdown scrolls (POS-01)
  readonly filteredSearchResults = computed<CatalogEntry[]>(() => searchProducts(this.productSearchTerm(), this.entries()));

  selectGroup(groupId: string): void {
    this.activeGroupId.set(groupId);
  }

  choose(product: Product, presentation: ProductPresentation | null = null): void {
    this.productSearchTerm.set('');
    this.add.emit({ product, presentation });
  }

  // CON-02 (096): at cost a row shows what it charges: the product's cost, or the package's (base quantity x cost)
  entryPrice(entry: CatalogEntry): number | null {
    if (!this.atCost()) return entry.price;
    return entry.presentation ? entry.presentation.cost : entry.product.buyPrice;
  }

  chooseEntry(entry: CatalogEntry): void {
    this.choose(entry.product, entry.presentation);
  }

  // Enter adds the product whose code is the one typed (not one of its packages), the one of a whole barcode, or the
  // only match; otherwise it waits for a click
  addFromSearch(): void {
    const entry = productForEnter(this.productSearchTerm(), this.entries(), this.filteredSearchResults());
    if (entry) {
      this.chooseEntry(entry);
    }
  }
}
