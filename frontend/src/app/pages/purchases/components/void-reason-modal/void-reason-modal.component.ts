import { Component, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';

// COM-09: pide el motivo de anulación de un pedido o un pago (RG-03); el servidor valida y revierte el dinero
@Component({
  selector: 'app-void-reason-modal',
  standalone: true,
  imports: [FormsModule, ModalComponent],
  templateUrl: './void-reason-modal.component.html',
  styleUrls: ['./void-reason-modal.component.css']
})
export class VoidReasonModalComponent {
  isOpen = input<boolean>(false);
  title = input<string>('Anular');
  message = input<string>('');
  isLoading = input<boolean>(false);
  errorMessage = input<string | null>(null);

  confirmed = output<string>();
  close = output<void>();

  voidReason = signal<string>('');

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.voidReason.set('');
      }
    });
  }

  get isValid(): boolean {
    return this.voidReason().trim().length > 0;
  }

  confirm(): void {
    if (this.isValid && !this.isLoading()) {
      this.confirmed.emit(this.voidReason().trim());
    }
  }

  onClose(): void {
    if (!this.isLoading()) {
      this.close.emit();
    }
  }
}
