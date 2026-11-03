import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Product } from '../../../../core/product/models/product.models';
import { PurchaseDraftLine } from '../../../../core/purchase/models/purchase.models';
import { searchProducts } from '../../../../shared/utils/product-search';

const MAX_RESULTS = 8;

// CA-03: read lines without a clear product. The person chooses a suggestion, searches, creates it or removes the line
@Component({
  selector: 'app-reading-lines-panel',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './reading-lines-panel.component.html',
  styleUrls: ['./reading-lines-panel.component.css']
})
export class ReadingLinesPanelComponent {
  lines = input<PurchaseDraftLine[]>([]);
  products = input<Product[]>([]);

  choose = output<{ index: number; product: Product }>();
  create = output<number>();
  discard = output<number>();

  // The search box belongs to the line it was typed in
  searchLine = signal<number | null>(null);
  searchTerm = signal<string>('');

  private byId = computed(() => new Map(this.products().map(product => [product.id, product])));

  results = computed(() => {
    const active = this.products().filter(product => product.status !== 'ELIMINADO');
    return searchProducts(this.searchTerm(), active).slice(0, MAX_RESULTS);
  });

  // A suggestion can leave the catalog between the reading and the choice
  suggestions(line: PurchaseDraftLine): Product[] {
    return line.suggestions.map(s => this.byId().get(s.id)).filter((p): p is Product => !!p);
  }

  onSearch(index: number, term: string): void {
    this.searchLine.set(index);
    this.searchTerm.set(term);
  }

  pick(index: number, product: Product): void {
    this.searchLine.set(null);
    this.searchTerm.set('');
    this.choose.emit({ index, product });
  }

  remove(index: number): void {
    this.searchLine.set(null);
    this.searchTerm.set('');
    this.discard.emit(index);
  }
}
