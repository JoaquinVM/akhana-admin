import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, signal } from '@angular/core';
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
export class VoidSaleModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() isLoading = false;
  @Input() sale: Sale | null = null;

  @Output() close = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<VoidSaleRequest>();

  voidReason = signal<string>('');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.voidReason.set('');
    }
  }

  get isValid(): boolean {
    return this.voidReason().trim().length >= 5;
  }

  handleConfirm(): void {
    if (!this.isValid || this.isLoading) return;
    this.confirmed.emit({ voidReason: this.voidReason().trim() });
  }

  handleClose(): void {
    this.close.emit();
  }
}
