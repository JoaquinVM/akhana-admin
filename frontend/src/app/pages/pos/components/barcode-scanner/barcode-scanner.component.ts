import { Component, DOCUMENT, DestroyRef, OnInit, computed, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Product, ProductPresentation, presentationByBarcode } from '../../../../core/product/models/product.models';
import { ProductService } from '../../../../core/product/product.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { canonicalBarcode, looksLikeBarcode } from '../../../../shared/utils/barcode';
import { ScanCapture } from '../../../../shared/utils/barcode-scan';
import { scanWarningBeep } from '../../../../shared/utils/scan-beep';

export const CLOSE_WINDOW_TO_SCAN = 'Cierra esta ventana para escanear.';
export const SCAN_LOOKUP_FAILED = 'No se pudo consultar el código. Intenta de nuevo.';

export function unknownBarcodeMessage(code: string): string {
  return `El código ${code} no está registrado. Búscalo por nombre o cárgalo en Productos.`;
}

export function inactiveProductMessage(product: Product): string {
  return `${product.name} está inactivo y no se vende.`;
}

// CAT-15: a package that is only bought (Display ×24) has a code but is not sold whole
export function packageNotSoldMessage(product: Product, presentation: ProductPresentation): string {
  return `El empaque ${presentation.name} de ${product.name} no se vende entero.`;
}

export interface ScannedProduct {
  product: Product;
  // CAT-15: the package whose code was read; null for the product's own code
  presentation?: ProductPresentation | null;
  // Not in the catalog the screen loaded: the code was registered after opening it
  fromServer: boolean;
}

/** POS-15: reads the USB reader on the sale screen and says which product to add; the cart stays in register-sale. */
@Component({
  selector: 'app-barcode-scanner',
  standalone: true,
  template: '',
  // Nothing to draw: it must not count as an item of the page's layout
  styles: ':host { display: none; }'
})
export class BarcodeScannerComponent implements OnInit {
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);
  private readonly productService = inject(ProductService);
  private readonly toast = inject(ToastService);

  // Active products of the catalog
  readonly products = input<Product[]>([]);
  readonly sessionOpen = input<boolean>(false);

  readonly scanned = output<ScannedProduct>();

  // The codes of the products and of their active packages (CAT-15)
  private readonly byBarcode = computed(() => {
    const index = new Map<string, Product>();
    for (const product of this.products()) {
      for (const barcode of product.barcodes ?? []) {
        index.set(barcode, product);
      }
      for (const presentation of product.presentations ?? []) {
        if (presentation.active && presentation.barcode) {
          index.set(presentation.barcode, product);
        }
      }
    }
    return index;
  });

  ngOnInit(): void {
    const capture = new ScanCapture(this.document, (code) => this.onScan(code));
    capture.install();
    this.destroyRef.onDestroy(() => capture.uninstall());
  }

  private onScan(code: string): void {
    if (!this.canAdd()) return;
    const canonical = canonicalBarcode(code);
    const known = this.byBarcode().get(canonical);
    if (known) {
      this.emitScan(known, canonical, false);
      return;
    }
    // A label with letters or symbols is no code of ours, and some of them make the server refuse the whole request
    if (!looksLikeBarcode(code)) {
      this.warn(unknownBarcodeMessage(code));
      return;
    }
    this.productService.getProductByBarcode(code).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (product) => {
        if (!this.canAdd()) return;
        if (product.status !== 'ACTIVO') {
          this.warn(inactiveProductMessage(product));
          return;
        }
        this.emitScan(product, canonical, true);
      },
      error: (error: HttpErrorResponse) => {
        this.warn(error.status === 404 ? unknownBarcodeMessage(code) : SCAN_LOOKUP_FAILED);
      }
    });
  }

  private emitScan(product: Product, barcode: string, fromServer: boolean): void {
    const presentation = presentationByBarcode(product, barcode);
    if (presentation && !presentation.sold) {
      this.warn(packageNotSoldMessage(product, presentation));
      return;
    }
    this.scanned.emit({ product, presentation, fromServer });
  }

  private canAdd(): boolean {
    if (!this.sessionOpen()) return false;
    // Every dialog of the app carries it, and it is in the page only while it is open
    if (this.document.querySelector('[aria-modal="true"]')) {
      this.warn(CLOSE_WINDOW_TO_SCAN);
      return false;
    }
    return true;
  }

  private warn(message: string): void {
    scanWarningBeep();
    this.toast.error(message);
  }
}
