import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ProductService } from '../../../core/product/product.service';
import {
  ProductImportResult,
  ProductImportRow,
  ProductImportRowStatus
} from '../../../core/product/models/product-import.models';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { buildXlsx } from '../../../shared/utils/xlsx-writer';

// Rejected rows and warnings show by default; existing ones only with their filter (CAT-06)
export type ImportRowFilter = 'PENDING' | 'REJECTED' | 'WARNING' | 'EXISTING';

const STATUS_LABELS: Record<ProductImportRowStatus, string> = {
  IMPORTED: 'Importada',
  EXISTING: 'Ya existente',
  REJECTED: 'Rechazada',
  WARNING: 'Con aviso'
};

// CAT-06: import products from the spreadsheet .xlsx in two steps, Revisar and Importar
@Component({
  selector: 'app-product-import-modal',
  standalone: true,
  imports: [ModalComponent],
  templateUrl: './product-import-modal.component.html',
  styleUrls: ['./product-import-modal.component.css']
})
export class ProductImportModalComponent {
  private readonly productService = inject(ProductService);

  isOpen = input<boolean>(false);
  close = output<void>();
  imported = output<ProductImportResult>();

  file = signal<File | null>(null);
  result = signal<ProductImportResult | null>(null);
  // false: result of Revisar (nothing saved); true: result of Importar
  isImported = signal<boolean>(false);
  isBusy = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  filter = signal<ImportRowFilter>('PENDING');

  readonly statusLabels = STATUS_LABELS;

  canImport = computed(() => {
    const result = this.result();
    return !!result && !this.isImported() && !this.isBusy() && result.importedCount + result.presentationCount > 0;
  });

  visibleRows = computed<ProductImportRow[]>(() => {
    const rows = this.result()?.rows ?? [];
    switch (this.filter()) {
      case 'PENDING':
        return rows.filter(r => r.status === 'REJECTED' || r.status === 'WARNING');
      default:
        return rows.filter(r => r.status === this.filter());
    }
  });

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    // Another file: it must be reviewed again
    this.result.set(null);
    this.isImported.set(false);
    this.errorMessage.set(null);
    this.filter.set('PENDING');
  }

  review(): void {
    const file = this.file();
    if (!file) return;
    this.run(this.productService.previewImport(file), false);
  }

  import(): void {
    const file = this.file();
    if (!file || !this.canImport()) return;
    this.run(this.productService.importProducts(file), true);
  }

  downloadRejected(): void {
    const rejected = (this.result()?.rows ?? []).filter(r => r.status === 'REJECTED');
    if (rejected.length === 0) return;
    const blob = buildXlsx('Rechazadas', [
      ['Fila', 'Codigo', 'Nombre', 'Motivo'],
      ...rejected.map(r => [r.rowNumber, r.code, r.name, r.messages.join(' ')])
    ]);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'productos-rechazados.xlsx';
    link.click();
    URL.revokeObjectURL(url);
  }

  onClose(): void {
    if (this.isBusy()) return;
    this.file.set(null);
    this.result.set(null);
    this.isImported.set(false);
    this.errorMessage.set(null);
    this.filter.set('PENDING');
    this.close.emit();
  }

  private run(request: ReturnType<ProductService['previewImport']>, saving: boolean): void {
    this.isBusy.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: result => {
        this.result.set(result);
        this.isImported.set(saving);
        this.filter.set('PENDING');
        this.isBusy.set(false);
        if (saving) {
          this.imported.emit(result);
        }
      },
      error: err => {
        this.isBusy.set(false);
        this.errorMessage.set(err?.error?.message || 'No se pudo leer el archivo. Intente nuevamente.');
      }
    });
  }
}
