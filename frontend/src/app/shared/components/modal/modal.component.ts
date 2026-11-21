import { Component, DestroyRef, HostListener, effect, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { isTopDialog, markDialogOpen } from './dialog-stack';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [IconComponent, CommonModule, CdkTrapFocus],
  templateUrl: './modal.component.html',
  styleUrls: ['./modal.component.css']
})
export class ModalComponent {
  isOpen = input<boolean>(false);
  title = input<string>('');
  subtitle = input<string>('');
  size = input<'sm' | 'md' | 'lg' | 'xl'>('md');
  showCloseButton = input<boolean>(true);
  closeOnBackdrop = input<boolean>(true);
  closeOnEscape = input<boolean>(true);
  customClass = input<string>('');

  close = output<void>();

  constructor() {
    effect(() => markDialogOpen(this, this.isOpen()));
    inject(DestroyRef).onDestroy(() => markDialogOpen(this, false));
  }

  @HostListener('document:keydown.escape', ['$event'])
  handleEscape(event: Event): void {
    if (this.isOpen() && this.closeOnEscape() && isTopDialog(this)) {
      event.preventDefault();
      this.onClose();
    }
  }

  onClose(): void {
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if (this.closeOnBackdrop() && (event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onClose();
    }
  }
}
