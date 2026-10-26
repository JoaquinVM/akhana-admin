import { Component, inject, input, signal } from '@angular/core';
import { ReportService } from '../../../../core/report/report.service';
import { ReportName } from '../../../../core/report/models/report.models';

// FIN-10: «Exportar a Excel» descarga el reporte de la pestaña con el mismo rango que se ve
@Component({
  selector: 'app-report-export-button',
  standalone: true,
  template: `
    <div class="export">
      <button type="button" class="btn btn-secondary" [disabled]="disabled() || isExporting()" (click)="export()">
        {{ isExporting() ? 'Exportando…' : 'Exportar a Excel' }}
      </button>
      @if (errorMessage()) {
        <span class="export-error" role="alert">{{ errorMessage() }}</span>
      }
    </div>
  `,
  styles: [`
    .export { display: flex; flex-direction: column; align-items: flex-end; gap: 0.25rem; margin-left: auto; }
    .export-error { font-size: 0.75rem; color: #b91c1c; }
  `]
})
export class ReportExportButtonComponent {
  private readonly reportService = inject(ReportService);

  report = input.required<ReportName>();
  from = input<string>('');
  to = input<string>('');
  disabled = input<boolean>(false);

  isExporting = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  export(): void {
    this.isExporting.set(true);
    this.errorMessage.set(null);
    this.reportService.exportReport(this.report(), { from: this.from() || null, to: this.to() || null }).subscribe({
      next: file => {
        const url = URL.createObjectURL(file.content);
        const link = document.createElement('a');
        link.href = url;
        link.download = file.fileName;
        link.click();
        URL.revokeObjectURL(url);
        this.isExporting.set(false);
      },
      error: err => {
        this.errorMessage.set(err?.error?.message || 'Error al exportar el reporte. Intente nuevamente.');
        this.isExporting.set(false);
      }
    });
  }
}
