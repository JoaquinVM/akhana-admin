import { Component, DOCUMENT, computed, effect, inject, input, output, signal } from '@angular/core';
import { canonicalBarcode } from '../../../shared/utils/barcode';
import { ScanCapture } from '../../../shared/utils/barcode-scan';

/**
 * CAT-10: barcodes of the product form. A scan only goes to this list: saving is always the form's button. The modal
 * projects this component even while closed, so the reader is listened to only while `active`.
 */
@Component({
  selector: 'app-product-barcodes-section',
  standalone: true,
  templateUrl: './product-barcodes-section.component.html',
  styleUrls: ['./product-barcodes-section.component.css']
})
export class ProductBarcodesSectionComponent {
  private readonly document = inject(DOCUMENT);

  readonly active = input<boolean>(false);
  readonly barcodes = input<string[]>([]);
  readonly modified = input<boolean>(false);
  readonly noBarcode = input<boolean>(false);
  readonly maxBarcodes = 5;

  readonly barcodesChange = output<string[]>();
  readonly clearMark = output<void>();

  readonly typed = signal<string>('');
  readonly canAdd = computed(() => this.typed().trim().length > 0);

  constructor() {
    effect((onCleanup) => {
      if (!this.active()) return;
      const capture = new ScanCapture(this.document, (code) => this.add(code));
      capture.install();
      onCleanup(() => capture.uninstall());
    });
  }

  add(code: string): void {
    const barcode = canonicalBarcode(code);
    if (barcode && !this.barcodes().includes(barcode)) {
      this.barcodesChange.emit([...this.barcodes(), barcode]);
    }
  }

  addTyped(event?: Event): void {
    // Enter here must not submit the product form
    event?.preventDefault();
    if (!this.canAdd()) return;
    this.add(this.typed());
    this.typed.set('');
  }

  remove(barcode: string): void {
    this.barcodesChange.emit(this.barcodes().filter((b) => b !== barcode));
  }
}
