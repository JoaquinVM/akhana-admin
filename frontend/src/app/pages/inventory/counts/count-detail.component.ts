import { Component, DOCUMENT, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InventoryService } from '../../../core/inventory/inventory.service';
import {
  CountProduct, StockCount, StockCountLineRequest, StockCountReview, stockQuantityText
} from '../../../core/inventory/models/inventory.models';
import { ProductService } from '../../../core/product/product.service';
import { Product, activePresentations } from '../../../core/product/models/product.models';
import { normalizeSearchText, searchProducts } from '../../../shared/utils/product-search';
import { ScanCapture } from '../../../shared/utils/barcode-scan';
import { scanWarningBeep } from '../../../shared/utils/scan-beep';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';

// What is being written for a product: whole packages of each kind and the loose base units
export interface CountDraft {
  packages: Record<string, number | null>;
  loose: number | null;
}

// INV-05: «3 Caja ×12 + 4» = 40, like the server
export function draftQuantity(product: CountProduct, draft: CountDraft): number {
  let total = Number(draft.loose) || 0;
  for (const pkg of product.packages) {
    total += (Number(draft.packages[pkg.id]) || 0) * pkg.baseQuantity;
  }
  return Math.round(total * 1000) / 1000;
}

export function draftRequest(product: CountProduct, draft: CountDraft): StockCountLineRequest {
  return {
    packages: product.packages
      .filter(pkg => (Number(draft.packages[pkg.id]) || 0) > 0)
      .map(pkg => ({ presentationId: pkg.id, quantity: Number(draft.packages[pkg.id]) })),
    loose: Number(draft.loose) || 0
  };
}

// A product of the catalog as the count shows it (one added by scanning or searching)
export function countProductOf(product: Product): CountProduct {
  return {
    productId: product.id, code: product.code, name: product.name, saleUnit: product.saleUnit,
    status: product.stockStatus ?? 'TO_COUNT', barcodes: product.barcodes ?? [],
    packages: activePresentations(product).map(p => ({ id: p.id, name: p.name, baseQuantity: p.baseQuantity, barcode: p.barcode ?? null })),
    countedQuantity: null, breakdown: null, countedBy: null, countedAt: null
  };
}

/**
 * INV-05, INV-18: a count from the phone or the counter. Blind: the screen never shows the stock while counting. Each
 * product takes whole packages and loose units; the reader or the camera add one of what they read. Reviewing shows
 * the difference in units and Bs; confirming generates the adjustments (or the initial load of a product to count).
 */
@Component({
  selector: 'app-count-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmModalComponent],
  templateUrl: './count-detail.component.html',
  styleUrls: ['../inventory-page.css', './count-detail.component.css']
})
export class CountDetailComponent implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  private readonly productService = inject(ProductService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly document = inject(DOCUMENT);
  private readonly destroyRef = inject(DestroyRef);

  readonly video = viewChild<ElementRef<HTMLVideoElement>>('cameraVideo');

  readonly count = signal<StockCount | null>(null);
  readonly extra = signal<CountProduct[]>([]);
  readonly catalog = signal<Product[]>([]);
  readonly drafts = signal<Record<string, CountDraft>>({});
  readonly saving = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly filter = signal('');
  readonly addSearch = signal('');
  readonly highlighted = signal<string | null>(null);
  readonly review = signal<StockCountReview | null>(null);
  readonly busy = signal(false);
  readonly confirmDiscard = signal(false);
  readonly cameraOpen = signal(false);
  readonly cameraError = signal<string | null>(null);
  private cameraControls: { stop(): void } | null = null;
  private lastCameraCode = '';
  private lastCameraAt = 0;

  readonly open = computed(() => this.count()?.status === 'ABIERTO');

  readonly products = computed<CountProduct[]>(() => {
    const listed = this.count()?.products ?? [];
    const ids = new Set(listed.map(p => p.productId));
    return [...listed, ...this.extra().filter(p => !ids.has(p.productId))];
  });

  readonly visible = computed(() => {
    const term = normalizeSearchText(this.filter().trim());
    return this.products().filter(p => !term || normalizeSearchText(`${p.code} ${p.name}`).includes(term));
  });

  readonly counted = computed(() => this.products().filter(p => p.countedQuantity != null).length);

  readonly addMatches = computed(() => this.addSearch().trim()
    ? searchProducts(this.addSearch(), this.catalog().filter(p => !p.specialType && p.stockControlled !== false)).slice(0, 15)
    : []);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.inventoryService.getCount(id).subscribe({
      next: count => this.count.set(count),
      error: err => this.errorMessage.set(err.error?.message || 'No se pudo cargar el conteo.')
    });
    this.productService.getProducts().subscribe({ next: products => this.catalog.set(products), error: () => undefined });
    // POS-15: the USB reader adds one of what it reads, wherever the cursor is
    const capture = new ScanCapture(this.document, code => this.onCode(code));
    capture.install();
    this.destroyRef.onDestroy(() => {
      capture.uninstall();
      this.stopCamera();
    });
  }

  draft(product: CountProduct): CountDraft {
    return this.drafts()[product.productId] ?? { packages: {}, loose: null };
  }

  setPackage(product: CountProduct, packageId: string, value: number | null): void {
    const current = this.draft(product);
    this.drafts.update(d => ({ ...d, [product.productId]: { ...current, packages: { ...current.packages, [packageId]: value } } }));
  }

  setLoose(product: CountProduct, value: number | null): void {
    const current = this.draft(product);
    this.drafts.update(d => ({ ...d, [product.productId]: { ...current, loose: value } }));
  }

  hasDraft(product: CountProduct): boolean {
    return product.productId in this.drafts();
  }

  draftTotal(product: CountProduct): string {
    return stockQuantityText(draftQuantity(product, this.draft(product)), product.saleUnit)
      + (product.saleUnit === 'KILOGRAM' ? '' : ' u');
  }

  unitLabel(product: CountProduct): string {
    return product.saleUnit === 'KILOGRAM' ? 'Sueltas (kg)' : 'Sueltas (u)';
  }

  save(product: CountProduct): void {
    const count = this.count();
    if (!count) return;
    this.saving.set(product.productId);
    this.errorMessage.set(null);
    this.inventoryService.saveCountLine(count.id, product.productId, draftRequest(product, this.draft(product))).subscribe({
      next: updated => {
        this.saving.set(null);
        this.count.set(updated);
        this.drafts.update(d => {
          const { [product.productId]: _, ...rest } = d;
          return rest;
        });
      },
      error: err => {
        this.saving.set(null);
        this.errorMessage.set(err.error?.message || 'No se pudo guardar lo contado.');
      }
    });
  }

  addProduct(product: Product): void {
    this.addSearch.set('');
    if (!this.products().some(p => p.productId === product.id)) {
      this.extra.update(list => [...list, countProductOf(product)]);
    }
    this.highlight(product.id);
  }

  // The reader and the camera: a package's code adds one package, a product's code one loose unit
  onCode(code: string): void {
    if (!this.open()) return;
    let target = this.products().find(p => p.barcodes.includes(code) || p.packages.some(pkg => pkg.barcode === code));
    if (!target) {
      const fromCatalog = this.catalog().find(p => !p.specialType && p.stockControlled !== false
        && ((p.barcodes ?? []).includes(code) || activePresentations(p).some(pp => pp.barcode === code)));
      if (!fromCatalog) {
        scanWarningBeep();
        this.errorMessage.set(`El código ${code} no es de ningún producto que se cuente.`);
        return;
      }
      target = countProductOf(fromCatalog);
      this.extra.update(list => [...list, target!]);
    }
    this.errorMessage.set(null);
    const pkg = target.packages.find(p => p.barcode === code);
    const current = this.draft(target);
    if (pkg) {
      this.setPackage(target, pkg.id, (Number(current.packages[pkg.id]) || 0) + 1);
    } else {
      this.setLoose(target, (Number(current.loose) || 0) + 1);
    }
    this.highlight(target.productId);
  }

  private highlight(productId: string): void {
    this.highlighted.set(productId);
    setTimeout(() => this.document.getElementById('count-' + productId)?.scrollIntoView({ block: 'center', behavior: 'smooth' }));
  }

  async startCamera(): Promise<void> {
    this.cameraError.set(null);
    this.cameraOpen.set(true);
    try {
      // Loaded only here, so the rest of the app does not carry the reader
      const { BrowserMultiFormatReader } = await import('@zxing/browser');
      const element = this.video()?.nativeElement;
      if (!element) return;
      const reader = new BrowserMultiFormatReader();
      this.cameraControls = await reader.decodeFromConstraints(
        { video: { facingMode: 'environment' } }, element, result => {
          if (!result) return;
          const code = result.getText();
          const now = Date.now();
          // The same code in front of the camera is read once until it moves away
          if (code === this.lastCameraCode && now - this.lastCameraAt < 1500) return;
          this.lastCameraCode = code;
          this.lastCameraAt = now;
          this.onCode(code);
        });
    } catch {
      this.cameraError.set('No se pudo abrir la cámara. Revisa el permiso del navegador o usa el lector.');
    }
  }

  stopCamera(): void {
    this.cameraControls?.stop();
    this.cameraControls = null;
    this.cameraOpen.set(false);
  }

  startReview(): void {
    const count = this.count();
    if (!count) return;
    if (Object.keys(this.drafts()).length > 0) {
      this.errorMessage.set('Guarda o borra lo que escribiste antes de revisar.');
      return;
    }
    this.busy.set(true);
    this.inventoryService.reviewCount(count.id).subscribe({
      next: review => {
        this.busy.set(false);
        this.review.set(review);
      },
      error: err => {
        this.busy.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo revisar el conteo.');
      }
    });
  }

  qty(value: number, saleUnit: CountProduct['saleUnit']): string {
    return stockQuantityText(value, saleUnit);
  }

  confirm(): void {
    const count = this.count();
    if (!count) return;
    this.busy.set(true);
    this.inventoryService.confirmCount(count.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success(`Conteo #${count.number} confirmado: el stock quedó ajustado.`);
        this.router.navigate(['/inventory/counts']);
      },
      error: err => {
        this.busy.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo confirmar el conteo.');
      }
    });
  }

  discard(): void {
    const count = this.count();
    if (!count) return;
    this.busy.set(true);
    this.inventoryService.discardCount(count.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirmDiscard.set(false);
        this.toast.success(`Conteo #${count.number} descartado.`);
        this.router.navigate(['/inventory/counts']);
      },
      error: err => {
        this.busy.set(false);
        this.confirmDiscard.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo descartar el conteo.');
      }
    });
  }
}
