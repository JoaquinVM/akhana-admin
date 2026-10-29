import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MigrationService } from '../../core/migration/migration.service';
import { MigrationFiles, MigrationMovementTotal, MigrationResult } from '../../core/migration/models/migration.models';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { buildXlsx } from '../../shared/utils/xlsx-writer';

type FileKey = 'recaudaciones' | 'pedidos';

// FIN-11: one-time historical migration of both spreadsheets, in two steps: Revisar and Importar
@Component({
  selector: 'app-migration',
  standalone: true,
  imports: [DecimalPipe, ConfirmModalComponent],
  templateUrl: './migration.component.html',
  styleUrls: ['./migration.component.css']
})
export class MigrationComponent {
  private readonly migrationService = inject(MigrationService);

  recaudaciones = signal<File | null>(null);
  pedidos = signal<File | null>(null);
  cutoffDate = signal<string>('');
  result = signal<MigrationResult | null>(null);
  isBusy = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  confirmOpen = signal<boolean>(false);

  canReview = computed(() => !!this.recaudaciones() && !!this.pedidos() && !!this.cutoffDate() && !this.isBusy());

  // All or nothing: with a single rejected row nothing is imported
  canImport = computed(() => {
    const result = this.result();
    return !!result && !result.imported && result.rejected.length === 0 && !this.isBusy();
  });

  // Movements per month, to compare with the Transacc. pivot table
  movementsByMonth = computed(() => {
    const groups = new Map<string, MigrationMovementTotal[]>();
    for (const movement of this.result()?.movements ?? []) {
      groups.set(movement.month, [...(groups.get(movement.month) ?? []), movement]);
    }
    return [...groups.entries()].map(([month, movements]) => ({ month, movements }));
  });

  onFileSelected(key: FileKey, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    (key === 'recaudaciones' ? this.recaudaciones : this.pedidos).set(file);
    this.resetResult();
  }

  onCutoffChange(event: Event): void {
    this.cutoffDate.set((event.target as HTMLInputElement).value);
    this.resetResult();
  }

  review(): void {
    const files = this.files();
    if (!files || !this.canReview()) return;
    this.run(this.migrationService.preview(files));
  }

  askImport(): void {
    if (this.canImport()) {
      this.confirmOpen.set(true);
    }
  }

  confirmImport(): void {
    const files = this.files();
    if (!files) return;
    this.run(this.migrationService.importHistory(files), () => this.confirmOpen.set(false));
  }

  downloadRejected(): void {
    const rejected = this.result()?.rejected ?? [];
    if (rejected.length === 0) return;
    const blob = buildXlsx('Rechazadas', [
      ['Hoja', 'Fila', 'Motivo'],
      ...rejected.map(r => [r.sheet, r.rowNumber, r.messages.join(' ')])
    ]);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'migracion-rechazadas.xlsx';
    link.click();
    URL.revokeObjectURL(url);
  }

  private files(): MigrationFiles | null {
    const recaudaciones = this.recaudaciones();
    const pedidos = this.pedidos();
    const cutoffDate = this.cutoffDate();
    return recaudaciones && pedidos && cutoffDate ? { recaudaciones, pedidos, cutoffDate } : null;
  }

  // Another file or cutoff date: it must be reviewed again
  private resetResult(): void {
    if (this.result()?.imported) return;
    this.result.set(null);
    this.errorMessage.set(null);
  }

  private run(request: ReturnType<MigrationService['preview']>, done?: () => void): void {
    this.isBusy.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: result => {
        this.result.set(result);
        this.isBusy.set(false);
        done?.();
      },
      error: err => {
        this.isBusy.set(false);
        done?.();
        this.errorMessage.set(err?.error?.message || 'No se pudieron leer los archivos. Intente nuevamente.');
      }
    });
  }
}
