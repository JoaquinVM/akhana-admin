import { Component, HostListener, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
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

  @HostListener('document:keydown.escape', ['$event'])
  handleEscape(event: Event): void {
    if (this.isOpen() && this.closeOnEscape()) {
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
