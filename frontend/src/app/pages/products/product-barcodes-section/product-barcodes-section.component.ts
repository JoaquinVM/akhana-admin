import { Component, computed, input, output, signal } from '@angular/core';
import { canonicalBarcode } from '../../../shared/utils/barcode';

// CAT-10: barcodes of the product form; the form listens to the reader and saving is always its button
@Component({
  selector: 'app-product-barcodes-section',
  standalone: true,
  templateUrl: './product-barcodes-section.component.html',
  styleUrls: ['./product-barcodes-section.component.css']
})
export class ProductBarcodesSectionComponent {
  readonly barcodes = input<string[]>([]);
  readonly modified = input<boolean>(false);
  readonly noBarcode = input<boolean>(false);
  readonly maxBarcodes = 5;

  readonly barcodesChange = output<string[]>();
  readonly clearMark = output<void>();

  readonly typed = signal<string>('');
  readonly canAdd = computed(() => this.typed().trim().length > 0);

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
