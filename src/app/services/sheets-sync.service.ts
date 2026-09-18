import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, catchError, map } from 'rxjs';
import { Sale, CashRegisterSession } from '../models/domain.model';
import { StorageService } from './storage.service';

@Injectable({
  providedIn: 'root'
})
export class SheetsSyncService {
  constructor(
    private http: HttpClient,
    private storageService: StorageService
  ) {}

  get isConfigured(): boolean {
    const config = this.storageService.getConfig();
    return !!config.googleAppsScriptUrl && config.googleAppsScriptUrl.trim().length > 0;
  }

  syncSaleToSheets(sale: Sale): Observable<{ success: boolean; message: string }> {
    const config = this.storageService.getConfig();
    if (!this.isConfigured) {
      return of({ success: true, message: 'Venta guardada localmente (Apps Script no configurado aún).' });
    }

    const payload = {
      action: 'sync_sale',
      sale
    };

    return this.http.post<any>(config.googleAppsScriptUrl!, JSON.stringify(payload)).pipe(
      map(res => ({ success: res.status === 'success', message: res.message || 'Sincronizado' })),
      catchError(err => of({ success: false, message: 'Error de sincronización con Google Sheets: ' + err.message }))
    );
  }

  syncRegisterClose(register: CashRegisterSession): Observable<{ success: boolean; message: string }> {
    const config = this.storageService.getConfig();
    if (!this.isConfigured) {
      return of({ success: true, message: 'Arqueo guardado localmente.' });
    }

    const payload = {
      action: 'sync_register_close',
      register
    };

    return this.http.post<any>(config.googleAppsScriptUrl!, JSON.stringify(payload)).pipe(
      map(res => ({ success: res.status === 'success', message: res.message || 'Arqueo sincronizado' })),
      catchError(err => of({ success: false, message: 'Error al enviar arqueo a Sheets: ' + err.message }))
    );
  }
}
