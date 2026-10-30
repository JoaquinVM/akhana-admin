import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Product } from '../../../../core/product/models/product.models';
import { QuickProductGroup } from '../../../../core/quick-product/models/quick-product.models';
import { QuickProductService } from '../../../../core/quick-product/quick-product.service';
import { productForEnter, searchProducts } from '../../../../shared/utils/product-search';

/**
 * Left column of the register-sale screen: product search (POS-01) and quick product groups (POS-02).
 * It only picks products; register-sale decides how each one goes into the cart (presentation, free line…).
 */
@Component({
  selector: 'app-sale-catalog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sale-catalog.component.html',
  styleUrls: ['./sale-catalog.component.css']
})
export class SaleCatalogComponent {
  readonly quickProductService = inject(QuickProductService);

  // Active products of the catalog
  readonly products = input<Product[]>([]);

  readonly add = output<Product>();
  readonly configure = output<void>();

  readonly productSearchTerm = signal<string>('');
  readonly activeGroupId = signal<string | null>(null);

  readonly activeGroup = computed<QuickProductGroup | null>(() => {
    const groups = this.quickProductService.groups();
    if (groups.length === 0) return null;
    const currentId = this.activeGroupId();
    if (!currentId) return groups[0];
    return groups.find((g) => g.id === currentId) ?? groups[0];
  });

  // Every match, best first; the dropdown scrolls (POS-01)
  readonly filteredSearchResults = computed<Product[]>(() => searchProducts(this.productSearchTerm(), this.products()));

  selectGroup(groupId: string): void {
    this.activeGroupId.set(groupId);
  }

  choose(product: Product): void {
    this.productSearchTerm.set('');
    this.add.emit(product);
  }

  // Enter adds the product whose code is the one typed, or the only match; otherwise it waits for a click
  addFromSearch(): void {
    const product = productForEnter(this.productSearchTerm(), this.products(), this.filteredSearchResults());
    if (product) {
      this.choose(product);
    }
  }
}
