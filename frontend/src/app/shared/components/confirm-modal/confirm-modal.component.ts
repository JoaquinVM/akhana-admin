import { Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-confirm-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './confirm-modal.component.html',
  styleUrls: ['./confirm-modal.component.css']
})
export class ConfirmModalComponent {
  isOpen = input<boolean>(false);
  title = input<string>('¿Confirmar acción?');
  message = input<string>('¿Está seguro de continuar con esta operación?');
  confirmText = input<string>('Confirmar');
  cancelText = input<string>('Cancelar');
  variant = input<'danger' | 'warning' | 'primary'>('danger');
  isLoading = input<boolean>(false);

  confirm = output<void>();
  cancel = output<void>();

  onConfirm(): void {
    if (!this.isLoading()) {
      this.confirm.emit();
    }
  }

  onCancel(): void {
    if (!this.isLoading()) {
      this.cancel.emit();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onCancel();
    }
  }
}
