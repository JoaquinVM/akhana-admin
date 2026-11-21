import { Component, DestroyRef, HostListener, effect, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { isTopDialog, markDialogOpen } from '../modal/dialog-stack';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-confirm-modal',
  standalone: true,
  imports: [IconComponent, CommonModule, CdkTrapFocus],
  templateUrl: './confirm-modal.component.html',
  styleUrls: ['./confirm-modal.component.css']
})
export class ConfirmModalComponent {
  isOpen = input<boolean>(false);
  title = input<string>('¿Confirmar acción?');
  message = input<string>('¿Seguro que quieres continuar?');
  confirmText = input<string>('Confirmar');
  cancelText = input<string>('Cancelar');
  variant = input<'danger' | 'warning' | 'primary'>('danger');
  isLoading = input<boolean>(false);

  confirm = output<void>();
  cancel = output<void>();

  constructor() {
    effect(() => markDialogOpen(this, this.isOpen()));
    inject(DestroyRef).onDestroy(() => markDialogOpen(this, false));
  }

  @HostListener('document:keydown.escape', ['$event'])
  handleEscape(event: Event): void {
    if (this.isOpen() && isTopDialog(this)) {
      event.preventDefault();
      this.onCancel();
    }
  }

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
