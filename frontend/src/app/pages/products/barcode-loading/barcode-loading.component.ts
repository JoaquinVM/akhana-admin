import { Component, DOCUMENT, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BarcodePendingProduct } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { canonicalBarcode } from '../../../shared/utils/barcode';
import { ScanCapture } from '../../../shared/utils/barcode-scan';
import { searchProducts } from '../../../shared/utils/product-search';

/** CAT-10: goes through the products without a barcode, most sold first, without stopping the store. */
@Component({
  selector: 'app-barcode-loading',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './barcode-loading.component.html',
  styleUrls: ['./barcode-loading.component.css']
})
export class BarcodeLoadingComponent implements OnInit {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productService = inject(ProductService);
  private readonly toast = inject(ToastService);

  readonly pending = signal<BarcodePendingProduct[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly isSaving = signal<boolean>(false);
  readonly loadError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly searchTerm = signal<string>('');
  readonly selectedId = signal<string | null>(null);
  readonly barcode = signal<string>('');

  readonly visible = computed(() => {
    const term = this.searchTerm().trim();
    return term ? searchProducts(term, this.pending()) : this.pending();
  });
  readonly selected = computed(() => this.pending().find((p) => p.id === this.selectedId()) ?? null);
  readonly canSave = computed(() => !!this.selected() && this.barcode().trim().length > 0 && !this.isSaving());

  ngOnInit(): void {
    const capture = new ScanCapture(this.document, (code) => this.barcode.set(canonicalBarcode(code)));
    capture.install();
    this.destroyRef.onDestroy(() => capture.uninstall());

    this.productService.getBarcodePending().subscribe({
      next: (products) => {
        this.pending.set(products);
        this.selectedId.set(products[0]?.id ?? null);
        this.isLoading.set(false);
      },
      error: () => {
        this.loadError.set('No se pudo cargar la lista de productos.');
        this.isLoading.set(false);
      }
    });
  }

  select(product: BarcodePendingProduct): void {
    this.selectedId.set(product.id);
    this.barcode.set('');
    this.error.set(null);
  }

  saveAndContinue(): void {
    const product = this.selected();
    if (!product || !this.canSave()) return;
    this.isSaving.set(true);
    this.error.set(null);
    this.productService.addBarcode(product.id, canonicalBarcode(this.barcode())).subscribe({
      next: () => {
        this.toast.success(`Código guardado en ${product.name}.`);
        this.removeAndContinue(product);
      },
      error: (err) => this.fail(err, 'No se pudo guardar el código.')
    });
  }

  markNoBarcode(): void {
    const product = this.selected();
    if (!product || this.isSaving()) return;
    this.isSaving.set(true);
    this.error.set(null);
    this.productService.markNoBarcode(product.id).subscribe({
      next: () => this.removeAndContinue(product),
      error: (err) => this.fail(err, 'No se pudo marcar el producto.')
    });
  }

  skip(): void {
    const product = this.selected();
    if (!product) return;
    const list = this.visible();
    const next = list[(list.indexOf(product) + 1) % list.length] ?? null;
    this.selectedId.set(next?.id ?? null);
    this.barcode.set('');
    this.error.set(null);
  }

  private removeAndContinue(product: BarcodePendingProduct): void {
    const list = this.visible();
    const after = list[list.indexOf(product) + 1] ?? list.find((p) => p !== product) ?? null;
    this.pending.update((products) => products.filter((p) => p !== product));
    this.selectedId.set(after?.id ?? this.pending()[0]?.id ?? null);
    this.barcode.set('');
    this.isSaving.set(false);
  }

  private fail(err: { error?: { message?: string } }, fallback: string): void {
    this.isSaving.set(false);
    this.error.set(err.error?.message || fallback);
  }
}
