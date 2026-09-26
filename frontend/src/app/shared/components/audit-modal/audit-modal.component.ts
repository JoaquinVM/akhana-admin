import { Component, computed, input, output } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';

export interface AuditField {
  key: string;
  label: string;
  value: string;
  isDate?: boolean;
}

const DEFAULT_LABELS: Record<string, string> = {
  createdBy: 'Usuario de creación',
  createdAt: 'Fecha y hora de creación',
  created_by: 'Usuario de creación',
  created_at: 'Fecha y hora de creación',
  updatedBy: 'Usuario de última edición',
  updatedAt: 'Fecha y hora de última edición',
  updated_by: 'Usuario de última edición',
  updated_at: 'Fecha y hora de última edición',
  deletedBy: 'Usuario de eliminación',
  deletedAt: 'Fecha y hora de eliminación',
  deleted_by: 'Usuario de eliminación',
  deleted_at: 'Fecha y hora de eliminación'
};

@Component({
  selector: 'app-audit-modal',
  standalone: true,
  imports: [CommonModule, DatePipe],
  templateUrl: './audit-modal.component.html',
  styleUrls: ['./audit-modal.component.css']
})
export class AuditModalComponent {
  isOpen = input<boolean>(false);
  title = input<string>('Información de Auditoría');
  subtitle = input<string>('');
  data = input<Record<string, any> | null>(null);
  labels = input<Record<string, string>>({});

  close = output<void>();

  // Señal computada que filtra estrictamente campos nulos, indefinidos o vacíos
  activeAuditFields = computed<AuditField[]>(() => {
    const rawData = this.data();
    if (!rawData) return [];

    const customLabels = this.labels();
    const result: AuditField[] = [];

    for (const [key, rawValue] of Object.entries(rawData)) {
      // Regla Mandatoria: No mostrar campos null, vacíos o undefined
      if (rawValue === null || rawValue === undefined) {
        continue;
      }

      const strVal = String(rawValue).trim();
      if (strVal === '' || strVal === 'null' || strVal === 'undefined') {
        continue;
      }

      const label = customLabels[key] || DEFAULT_LABELS[key] || this.formatFallbackKey(key);
      const isDate = this.isDateKeyOrValue(key, strVal);

      result.push({
        key,
        label,
        value: strVal,
        isDate
      });
    }

    return result;
  });

  onClose(): void {
    this.close.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onClose();
    }
  }

  private isDateKeyOrValue(key: string, val: string): boolean {
    const lowerKey = key.toLowerCase();
    if (lowerKey.includes('date') || lowerKey.includes('at')) {
      return !isNaN(Date.parse(val));
    }
    return false;
  }

  private formatFallbackKey(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/_/g, ' ')
      .replace(/^./, str => str.toUpperCase())
      .trim();
  }
}
