import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { SearchSelectComponent, SearchSelectOption } from '../../../../shared/components/search-select/search-select.component';
import { StoreDayPipe } from '../../../../shared/utils/store-day.pipe';
import { reducePhoto } from '../../../../shared/utils/reduce-photo';
import { PurchaseReadingService } from '../../../../core/purchase/purchase-reading.service';
import { Purchase, PurchaseDraft, PurchaseReadingSource } from '../../../../core/purchase/models/purchase.models';
import { Supplier } from '../../../../core/supplier/models/supplier.models';

export interface DraftRead {
  draft: PurchaseDraft;
  file: File | null;
  // CA-12: the pending purchase this photo completes; null is a new purchase
  completePurchaseId: string | null;
}

// COM-20: asks for the text or the photo and answers a draft. Nothing is registered here
@Component({
  selector: 'app-purchase-reading-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent, SearchSelectComponent, StoreDayPipe],
  templateUrl: './purchase-reading-modal.component.html',
  styleUrls: ['./purchase-reading-modal.component.css']
})
export class PurchaseReadingModalComponent {
  private readonly readingService = inject(PurchaseReadingService);

  mode = input<PurchaseReadingSource | null>(null);
  // CA-09: the arrival of this purchase is being read, so no other purchase is offered
  forPurchase = input<Purchase | null>(null);
  suppliers = input<Supplier[]>([]);

  draftRead = output<DraftRead>();
  fillByHand = output<void>();
  close = output<void>();

  text = signal<string>('');
  // COM-21: optional; a message rarely names who it is for, and its products come first
  supplierId = signal<string>('');
  supplierOptions = computed<SearchSelectOption[]>(() => this.suppliers()
    .filter(s => s.status === 'ACTIVO')
    .map(s => ({ value: s.id, label: s.name, hint: s.code })));
  isReading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  // CA-12: read, waiting for the person to say whether it completes a pending purchase
  pendingChoice = signal<{ draft: PurchaseDraft; file: File } | null>(null);

  title = computed(() => {
    const purchase = this.forPurchase();
    if (purchase) {
      return 'Llegó la mercadería · ' + purchase.purchaseNumber;
    }
    return this.mode() === 'TEXT' ? 'Pedido desde texto' : 'Pedido desde foto';
  });

  constructor() {
    effect(() => {
      if (this.mode()) {
        untracked(() => {
          this.text.set('');
          this.supplierId.set('');
          this.errorMessage.set(null);
          this.isReading.set(false);
          this.pendingChoice.set(null);
        });
      }
    });
  }

  // CA-24: the pasted text stays in the box when the reading fails
  readText(): void {
    const text = this.text().trim();
    if (!text) {
      this.errorMessage.set('Pega el texto del pedido.');
      return;
    }
    this.isReading.set(true);
    this.errorMessage.set(null);
    this.readingService.readText(text, this.supplierId() || null).subscribe({
      next: draft => {
        this.isReading.set(false);
        this.draftRead.emit({ draft, file: null, completePurchaseId: null });
      },
      error: err => this.fail(err)
    });
  }

  async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.isReading.set(true);
    this.errorMessage.set(null);
    this.readPhoto(await reducePhoto(file));
  }

  readPhoto(file: File): void {
    this.readingService.readPhoto(file).subscribe({
      next: draft => {
        this.isReading.set(false);
        if (!this.forPurchase() && draft.pendingPurchases.length > 0) {
          this.pendingChoice.set({ draft, file });
        } else {
          this.draftRead.emit({ draft, file, completePurchaseId: null });
        }
      },
      error: err => this.fail(err)
    });
  }

  choose(purchaseId: string | null): void {
    const choice = this.pendingChoice();
    if (choice) {
      this.draftRead.emit({ ...choice, completePurchaseId: purchaseId });
    }
  }

  onClose(): void {
    if (!this.isReading()) {
      this.close.emit();
    }
  }

  private fail(err: { error?: { message?: string } }): void {
    this.isReading.set(false);
    this.errorMessage.set(err.error?.message || 'No se pudo leer. Puedes intentar de nuevo o llenarlo a mano.');
  }
}
