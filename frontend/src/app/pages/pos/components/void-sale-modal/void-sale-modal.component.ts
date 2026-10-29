import { Component, effect, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Sale, VoidSaleRequest } from '../../../../core/sale/models/sale.models';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

@Component({
  selector: 'app-void-sale-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, ModalComponent],
  templateUrl: './void-sale-modal.component.html',
  styleUrls: ['./void-sale-modal.component.css']
})
export class VoidSaleModalComponent {
  readonly isOpen = input<boolean>(false);
  readonly isLoading = input<boolean>(false);
  readonly sale = input<Sale | null>(null);

  readonly close = output<void>();
  readonly confirmed = output<VoidSaleRequest>();

  voidReason = signal<string>('');

  constructor() {
    // Each opening starts with an empty reason
    effect(() => {
      if (this.isOpen()) {
        this.voidReason.set('');
      }
    });
  }

  // RG-03: the reason is required, with no minimum length, like every other void
  get isValid(): boolean {
    return this.voidReason().trim().length > 0;
  }

  handleConfirm(): void {
    if (!this.isValid || this.isLoading()) return;
    this.confirmed.emit({ voidReason: this.voidReason().trim() });
  }

  handleClose(): void {
    this.close.emit();
  }
}
