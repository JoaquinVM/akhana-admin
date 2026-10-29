import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MigrationFiles, MigrationResult, MigrationStatus } from './models/migration.models';

// FIN-11: Revisar saves nothing; Importar validates again and saves all or nothing
@Injectable({
  providedIn: 'root'
})
export class MigrationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/migration';

  status(): Observable<MigrationStatus> {
    return this.http.get<MigrationStatus>(`${this.apiUrl}/status`);
  }

  preview(files: MigrationFiles): Observable<MigrationResult> {
    return this.http.post<MigrationResult>(`${this.apiUrl}/preview`, this.form(files));
  }

  importHistory(files: MigrationFiles): Observable<MigrationResult> {
    return this.http.post<MigrationResult>(this.apiUrl, this.form(files));
  }

  private form(files: MigrationFiles): FormData {
    const form = new FormData();
    form.append('recaudaciones', files.recaudaciones);
    form.append('pedidos', files.pedidos);
    form.append('cutoffDate', files.cutoffDate);
    return form;
  }
}
