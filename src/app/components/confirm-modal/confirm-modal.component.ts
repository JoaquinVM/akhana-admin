import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-confirm-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="isOpen" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs transition-opacity">
      <div class="bg-surface-container-lowest rounded-2xl shadow-2xl border border-outline-variant max-w-md w-full overflow-hidden transform transition-all">
        <!-- Header -->
        <div class="p-6 pb-4 flex items-start gap-4">
          <div [ngClass]="isDanger ? 'bg-red-100 text-red-700' : 'bg-amber-100 text-amber-700'" class="p-3 rounded-xl flex items-center justify-center shrink-0">
            <span class="material-symbols-outlined text-2xl">{{ isDanger ? 'warning' : 'help' }}</span>
          </div>
          <div class="flex-1">
            <h3 class="text-lg font-bold font-headline text-on-surface">{{ title }}</h3>
            <p class="text-sm text-on-surface-variant mt-1.5 leading-relaxed">{{ message }}</p>
          </div>
        </div>

        <!-- Optional Input for Cancellation Reason -->
        <div *ngIf="requireInput" class="px-6 py-2">
          <label class="block text-xs font-semibold text-on-surface-variant uppercase tracking-wider mb-1.5">{{ inputLabel || 'Motivo' }}</label>
          <input
            #inputField
            type="text"
            [placeholder]="inputPlaceholder || 'Ingrese el motivo...'"
            [value]="inputValue"
            (input)="inputValue = inputField.value"
            class="w-full px-3.5 py-2.5 text-sm rounded-lg border border-outline-variant focus:outline-hidden focus:ring-2 focus:ring-primary focus:border-primary bg-surface-container-low text-on-surface"
          />
        </div>

        <!-- Actions -->
        <div class="p-4 px-6 bg-surface-container-low flex justify-end gap-3 border-t border-outline-variant/40">
          <button
            type="button"
            (click)="onCancel()"
            class="px-4 py-2 text-sm font-semibold text-on-surface-variant hover:text-on-surface bg-surface-container hover:bg-surface-container-high rounded-xl transition-colors">
            {{ cancelText }}
          </button>
          <button
            type="button"
            (click)="onConfirm()"
            [disabled]="requireInput && !inputValue.trim()"
            [ngClass]="isDanger ? 'bg-error hover:bg-red-800 text-white' : 'bg-primary hover:bg-primary-container text-white'"
            class="px-5 py-2 text-sm font-semibold rounded-xl shadow-xs transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-1.5">
            <span class="material-symbols-outlined text-base">check</span>
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmModalComponent {
  @Input() isOpen = false;
  @Input() title = 'Confirmar Acción';
  @Input() message = '¿Está seguro de que desea continuar?';
  @Input() confirmText = 'Confirmar';
  @Input() cancelText = 'Cancelar';
  @Input() isDanger = false;
  @Input() requireInput = false;
  @Input() inputLabel = 'Motivo de la Anulación';
  @Input() inputPlaceholder = 'Escriba la justificación...';
  @Input() inputValue = '';

  @Output() confirmed = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  onConfirm(): void {
    this.confirmed.emit(this.inputValue);
    this.inputValue = '';
  }

  onCancel(): void {
    this.cancelled.emit();
    this.inputValue = '';
  }
}
